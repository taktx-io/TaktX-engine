/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.engine.security;

import io.taktx.dto.Constants;
import io.taktx.dto.DlqEntryDTO;
import io.taktx.dto.DlqReasonCode;
import io.taktx.dto.ProcessInstanceDlqEntryDTO;
import io.taktx.dto.ProcessInstanceTriggerDTO;
import io.taktx.dto.ReplayProtectionMode;
import io.taktx.dto.TokenClaims;
import io.taktx.engine.dlq.DlqHeaders;
import io.taktx.engine.pi.ProcessInstanceTriggerEnvelope;
import io.taktx.security.AuthorizationTokenException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.Headers;
import org.apache.kafka.streams.processor.PunctuationType;
import org.apache.kafka.streams.processor.api.Processor;
import org.apache.kafka.streams.processor.api.ProcessorContext;
import org.apache.kafka.streams.processor.api.Record;
import org.apache.kafka.streams.state.KeyValueStore;

/**
 * Enforces durable replay protection for JWT-bearing entry commands before they reach the main
 * process-instance processor.
 */
@Slf4j
public class ReplayProtectionProcessor
    implements Processor<String, ProcessInstanceTriggerEnvelope, Object, Object> {

  private static final Duration CLEANUP_INTERVAL = Duration.ofMinutes(1);
  private static final String DLQ_REASON_HINT_HEADER = DlqHeaders.REASON_HINT;
  private static final String DLQ_REASON_TEXT_HEADER = DlqHeaders.REASON_TEXT;
  private static final String DLQ_CAPTURE_STAGE_HEADER = DlqHeaders.CAPTURE_STAGE;

  private final Clock clock;
  private final EngineAuthorizationService engineAuthorizationService;
  private final String replayStoreName;

  private ProcessorContext<Object, Object> context;
  private KeyValueStore<String, Long> replayStore;

  public ReplayProtectionProcessor(
      Clock clock, EngineAuthorizationService engineAuthorizationService, String replayStoreName) {
    this.clock = clock;
    this.engineAuthorizationService = engineAuthorizationService;
    this.replayStoreName = replayStoreName;
  }

  @Override
  public void init(ProcessorContext<Object, Object> context) {
    this.context = context;
    this.replayStore = context.getStateStore(replayStoreName);
    context.schedule(CLEANUP_INTERVAL, PunctuationType.WALL_CLOCK_TIME, this::purgeExpiredEntries);
  }

  @Override
  public void process(Record<String, ProcessInstanceTriggerEnvelope> inputRecord) {
    if (inputRecord == null
        || inputRecord.value() == null
        || inputRecord.value().trigger() == null) {
      return;
    }

    ProcessInstanceTriggerEnvelope envelope = inputRecord.value();
    ProcessInstanceTriggerDTO trigger = envelope.trigger();
    UUID processInstanceId = trigger.getProcessInstanceId();
    Header authHeader =
        inputRecord.headers() != null
            ? inputRecord.headers().lastHeader(Constants.HEADER_AUTHORIZATION)
            : null;

    if (processInstanceId == null || authHeader == null || authHeader.value() == null) {
      forwardEnvelope(inputRecord, envelope);
      return;
    }

    if (!engineAuthorizationService.isEntryAuthorizationGateActive()
        || !engineAuthorizationService.isReplayProtectionActive()) {
      forwardEnvelope(inputRecord, envelope);
      return;
    }

    try {
      TokenClaims claims = engineAuthorizationService.validateJwtClaims(authHeader, trigger);
      ReplayProtectionMode mode = engineAuthorizationService.replayProtectionMode();
      String auditId = claims.getAuditId();

      if (mode == ReplayProtectionMode.OFF) {
        forwardEnvelope(inputRecord, envelope);
        return;
      }

      if (auditId == null || auditId.isBlank()) {
        if (mode == ReplayProtectionMode.STRICT) {
          log.warn(
              "Rejected entry command {} for processInstanceId={} — replayProtectionMode=STRICT requires non-blank auditId",
              trigger.getClass().getSimpleName(),
              processInstanceId);
          return;
        }
        forwardEnvelope(inputRecord, envelope.withValidatedJwtClaims(claims));
        return;
      }

      long now = clock.millis();
      long retentionMs = engineAuthorizationService.replayProtectionRetentionMs();
      String replayKey = engineAuthorizationService.canonicalReplayKey(claims);
      Long storedValue = replayStore.get(replayKey);
      if (storedValue != null) {
        long firstSeenTs = Math.abs(storedValue);
        if (now - firstSeenTs < retentionMs) {
          log.warn(
              "Rejected replayed entry command {} for processInstanceId={} auditId={} replayKey={} retentionMs={}",
              trigger.getClass().getSimpleName(),
              processInstanceId,
              auditId,
              replayKey,
              retentionMs);
          // Rate-gate: storedValue > 0 means first detection — emit DLQ entry once.
          // Negative storedValue is the sentinel: DLQ already emitted for this replayKey.
          if (storedValue > 0) {
            emitReplayDlq(inputRecord, envelope, processInstanceId, auditId, replayKey);
            replayStore.put(replayKey, -storedValue);
          }
          return;
        }
      }

      replayStore.put(replayKey, now);
      forwardEnvelope(inputRecord, envelope.withValidatedJwtClaims(claims));
    } catch (AuthorizationTokenException e) {
      log.warn(
          "Rejected entry command {} for processInstanceId={} during replay precheck: {}",
          trigger.getClass().getSimpleName(),
          processInstanceId,
          e.getMessage());
    }
  }

  private void purgeExpiredEntries(long timestamp) {
    DedupStoreSupport.purgeExpiredEntries(
        replayStore, timestamp, engineAuthorizationService.replayProtectionRetentionMs());
  }

  private void emitReplayDlq(
      Record<String, ProcessInstanceTriggerEnvelope> inputRecord,
      ProcessInstanceTriggerEnvelope envelope,
      UUID processInstanceId,
      String auditId,
      String replayKey) {
    Map<String, byte[]> headersMap = headersToMap(inputRecord.headers());
    headersMap.put(
        DLQ_REASON_HINT_HEADER,
        DlqReasonCode.REPLAY_DETECTED.name().getBytes(StandardCharsets.UTF_8));
    headersMap.put(
        DLQ_REASON_TEXT_HEADER,
        ("Replay attack detected: auditId=" + auditId + " replayKey=" + replayKey)
            .getBytes(StandardCharsets.UTF_8));
    headersMap.put(DLQ_CAPTURE_STAGE_HEADER, "PROCESSOR".getBytes(StandardCharsets.UTF_8));
    DlqEntryDTO dlqEntry =
        new ProcessInstanceDlqEntryDTO(
            processInstanceId, envelope.trigger(), headersMap, envelope.data());
    context.forward(new Record<>(null, dlqEntry, clock.millis()));
  }

  private static Map<String, byte[]> headersToMap(Headers headers) {
    Map<String, byte[]> result = new HashMap<>();
    if (headers != null) {
      for (Header header : headers) {
        result.put(header.key(), header.value());
      }
    }
    return result;
  }

  private void forwardEnvelope(
      Record<String, ProcessInstanceTriggerEnvelope> inputRecord,
      ProcessInstanceTriggerEnvelope envelope) {
    context.forward(
        new Record<>(
            envelope.trigger().getProcessInstanceId(),
            envelope,
            inputRecord.timestamp(),
            inputRecord.headers()));
  }
}
