/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.engine.security;

import io.taktx.dto.Constants;
import io.taktx.dto.ExternalTaskResponseTriggerDTO;
import io.taktx.dto.ProcessInstanceTriggerDTO;
import io.taktx.dto.UserTaskResponseTriggerDTO;
import io.taktx.engine.pi.ProcessInstanceTriggerEnvelope;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.streams.processor.PunctuationType;
import org.apache.kafka.streams.processor.api.Processor;
import org.apache.kafka.streams.processor.api.ProcessorContext;
import org.apache.kafka.streams.processor.api.Record;
import org.apache.kafka.streams.state.KeyValueStore;

/** Deduplicates external-task and user-task responses on the UUID-keyed process-instance stream. */
@Slf4j
public class ProcessInstanceResponseDedupProcessor
    implements Processor<
        UUID, ProcessInstanceTriggerEnvelope, UUID, ProcessInstanceTriggerEnvelope> {

  private static final Duration CLEANUP_INTERVAL = Duration.ofMinutes(1);

  private final Clock clock;
  private final long retentionMs;
  private final String storeName;

  private ProcessorContext<UUID, ProcessInstanceTriggerEnvelope> context;
  private KeyValueStore<String, Long> store;

  public ProcessInstanceResponseDedupProcessor(Clock clock, long retentionMs, String storeName) {
    this.clock = clock;
    this.retentionMs = retentionMs;
    this.storeName = storeName;
  }

  @Override
  public void init(ProcessorContext<UUID, ProcessInstanceTriggerEnvelope> context) {
    this.context = context;
    this.store = context.getStateStore(storeName);
    context.schedule(CLEANUP_INTERVAL, PunctuationType.WALL_CLOCK_TIME, this::purgeExpiredEntries);
  }

  @Override
  public void process(Record<UUID, ProcessInstanceTriggerEnvelope> inputRecord) {
    if (inputRecord == null
        || inputRecord.value() == null
        || inputRecord.value().trigger() == null) {
      return;
    }

    ProcessInstanceTriggerEnvelope envelope = inputRecord.value();
    ProcessInstanceTriggerDTO trigger = envelope.trigger();
    String dedupKey = dedupKey(inputRecord, envelope);
    if (dedupKey == null) {
      forward(inputRecord, envelope);
      return;
    }

    long now = clock.millis();
    Long storedTs = store.get(dedupKey);
    if (storedTs != null && now - storedTs < retentionMs) {
      log.warn(
          "Rejected duplicate process-instance response {} for processInstanceId={} dedupKey={} retentionMs={}",
          trigger.getClass().getSimpleName(),
          trigger.getProcessInstanceId(),
          dedupKey,
          retentionMs);
      return;
    }

    store.put(dedupKey, now);
    forward(inputRecord, envelope);
  }

  private void purgeExpiredEntries(long timestamp) {
    DedupStoreSupport.purgeExpiredEntries(store, timestamp, retentionMs);
  }

  private static String dedupKey(
      Record<UUID, ProcessInstanceTriggerEnvelope> inputRecord,
      ProcessInstanceTriggerEnvelope envelope) {
    ProcessInstanceTriggerDTO trigger = envelope.trigger();
    String triggerNamespace = null;
    String messageId = null;
    if (trigger instanceof ExternalTaskResponseTriggerDTO externalTaskResponseTrigger) {
      triggerNamespace = ExternalTaskResponseTriggerDTO.class.getSimpleName();
      messageId = externalTaskResponseTrigger.getMessageId();
    } else if (trigger instanceof UserTaskResponseTriggerDTO userTaskResponseTrigger) {
      triggerNamespace = UserTaskResponseTriggerDTO.class.getSimpleName();
      messageId = userTaskResponseTrigger.getMessageId();
    }

    if (triggerNamespace == null) {
      return null;
    }

    UUID processInstanceId = trigger.getProcessInstanceId();
    String identity =
        messageId != null && !messageId.isBlank()
            ? "messageId:" + messageId
            : "payloadHash:" + signedPayloadHash(inputRecord, envelope);
    return triggerNamespace + ":" + processInstanceId + ":" + identity;
  }

  private static String signedPayloadHash(
      Record<UUID, ProcessInstanceTriggerEnvelope> inputRecord,
      ProcessInstanceTriggerEnvelope envelope) {
    byte[] payload = envelope.data() != null ? envelope.data() : new byte[0];
    Header signatureHeader =
        inputRecord.headers() != null
            ? inputRecord.headers().lastHeader(Constants.HEADER_ENGINE_SIGNATURE)
            : null;
    byte[] signatureBytes =
        signatureHeader != null && signatureHeader.value() != null
            ? signatureHeader.value()
            : new byte[0];

    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      digest.update(signatureBytes);
      digest.update((byte) 0x00);
      digest.update(payload);
      return toHex(digest.digest());
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 not available", e);
    }
  }

  private static String toHex(byte[] bytes) {
    StringBuilder hex = new StringBuilder(bytes.length * 2);
    for (byte current : bytes) {
      hex.append(String.format("%02x", current));
    }
    return hex.toString();
  }

  private void forward(
      Record<UUID, ProcessInstanceTriggerEnvelope> inputRecord,
      ProcessInstanceTriggerEnvelope envelope) {
    context.forward(
        new Record<>(inputRecord.key(), envelope, inputRecord.timestamp(), inputRecord.headers()));
  }
}
