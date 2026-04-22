/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.engine.security;

import io.taktx.dto.Constants;
import io.taktx.dto.ProcessInstanceTriggerDTO;
import io.taktx.dto.ReplayProtectionMode;
import io.taktx.dto.TokenClaims;
import io.taktx.engine.pi.ProcessInstanceTriggerEnvelope;
import io.taktx.security.AuthorizationTokenException;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.streams.processor.PunctuationType;
import org.apache.kafka.streams.processor.api.Processor;
import org.apache.kafka.streams.processor.api.ProcessorContext;
import org.apache.kafka.streams.processor.api.Record;
import org.apache.kafka.streams.state.KeyValueIterator;
import org.apache.kafka.streams.state.KeyValueStore;

/**
 * Enforces durable replay protection for JWT-bearing entry commands before they reach the main
 * process-instance processor.
 */
@Slf4j
public class ReplayProtectionProcessor
    implements Processor<
        String, ProcessInstanceTriggerEnvelope, UUID, ProcessInstanceTriggerEnvelope> {

  private static final Duration CLEANUP_INTERVAL = Duration.ofMinutes(1);

  private final Clock clock;
  private final EngineAuthorizationService engineAuthorizationService;
  private final String replayStoreName;

  private ProcessorContext<UUID, ProcessInstanceTriggerEnvelope> context;
  private KeyValueStore<String, Long> replayStore;

  public ReplayProtectionProcessor(
      Clock clock, EngineAuthorizationService engineAuthorizationService, String replayStoreName) {
    this.clock = clock;
    this.engineAuthorizationService = engineAuthorizationService;
    this.replayStoreName = replayStoreName;
  }

  @Override
  public void init(ProcessorContext<UUID, ProcessInstanceTriggerEnvelope> context) {
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
      forward(inputRecord, envelope);
      return;
    }

    if (!engineAuthorizationService.isEntryAuthorizationGateActive()
        || !engineAuthorizationService.isReplayProtectionActive()) {
      forward(inputRecord, envelope);
      return;
    }

    try {
      TokenClaims claims = engineAuthorizationService.validateJwtClaims(authHeader, trigger);
      ReplayProtectionMode mode = engineAuthorizationService.replayProtectionMode();
      String auditId = claims.getAuditId();

      if (mode == ReplayProtectionMode.OFF) {
        forward(inputRecord, envelope);
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
        forward(inputRecord, envelope.withValidatedJwtClaims(claims));
        return;
      }

      long now = clock.millis();
      long retentionMs = engineAuthorizationService.replayProtectionRetentionMs();
      String replayKey = engineAuthorizationService.canonicalReplayKey(claims);
      Long existingTimestamp = replayStore.get(replayKey);
      if (existingTimestamp != null && now - existingTimestamp < retentionMs) {
        log.warn(
            "Rejected replayed entry command {} for processInstanceId={} auditId={} replayKey={} retentionMs={}",
            trigger.getClass().getSimpleName(),
            processInstanceId,
            auditId,
            replayKey,
            retentionMs);
        return;
      }

      replayStore.put(replayKey, now);
      forward(inputRecord, envelope.withValidatedJwtClaims(claims));
    } catch (AuthorizationTokenException e) {
      log.warn(
          "Rejected entry command {} for processInstanceId={} during replay precheck: {}",
          trigger.getClass().getSimpleName(),
          processInstanceId,
          e.getMessage());
    }
  }

  private void purgeExpiredEntries(long timestamp) {
    long retentionMs = engineAuthorizationService.replayProtectionRetentionMs();
    List<String> expiredKeys = new ArrayList<>();
    try (KeyValueIterator<String, Long> entries = replayStore.all()) {
      while (entries.hasNext()) {
        org.apache.kafka.streams.KeyValue<String, Long> entry = entries.next();
        if (entry.value != null && timestamp - entry.value >= retentionMs) {
          expiredKeys.add(entry.key);
        }
      }
    }
    expiredKeys.forEach(replayStore::delete);
  }

  private void forward(
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
