/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.engine.pd;

import io.taktx.dto.UserTaskResponseDlqEntryDTO;
import io.taktx.dto.UserTaskResponseTriggerDTO;
import io.taktx.engine.dlq.DlqHeaders;
import io.taktx.engine.security.ProtectedDataPlaneParticipationGuard;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.streams.processor.api.Processor;
import org.apache.kafka.streams.processor.api.ProcessorContext;
import org.apache.kafka.streams.processor.api.Record;

/**
 * Processor for the {@code usertasks-response} ingress topic. On success, forwards the {@link
 * UserTaskResponseTriggerDTO} to the process-instance trigger stream. On deserializer failure
 * (surfacing here as a null value) or processing exception, emits a {@link
 * UserTaskResponseDlqEntryDTO} to DLQ.
 */
@Slf4j
public class UserTaskResponseProcessor
    implements Processor<UUID, UserTaskResponseTriggerDTO, Object, Object> {

  private static final String DLQ_REASON_HINT_HEADER = DlqHeaders.REASON_HINT;
  private static final String DLQ_REASON_TEXT_HEADER = DlqHeaders.REASON_TEXT;
  private static final String DLQ_CAPTURE_STAGE_HEADER = DlqHeaders.CAPTURE_STAGE;

  private final Clock clock;
  private final ProtectedDataPlaneParticipationGuard protectedDataPlaneParticipationGuard;

  private ProcessorContext<Object, Object> context;

  public UserTaskResponseProcessor(Clock clock) {
    this(clock, null);
  }

  public UserTaskResponseProcessor(
      Clock clock, ProtectedDataPlaneParticipationGuard protectedDataPlaneParticipationGuard) {
    this.clock = clock;
    this.protectedDataPlaneParticipationGuard = protectedDataPlaneParticipationGuard;
  }

  @Override
  public void init(ProcessorContext<Object, Object> context) {
    this.context = context;
  }

  @Override
  public void process(Record<UUID, UserTaskResponseTriggerDTO> userTaskResponseTriggerRecord) {
    if (userTaskResponseTriggerRecord.value() == null) {
      log.warn("⚠ Null decoded payload on usertasks-response, routing to DLQ");
      emitUserTaskResponseDlq(
          userTaskResponseTriggerRecord,
          "PAYLOAD_DESERIALIZATION_ERROR",
          "Null decoded payload for usertasks-response record",
          "DESERIALIZER");
      return;
    }
    try {
      if (shouldBlockProtectedDataPlane(userTaskResponseTriggerRecord)) {
        return;
      }
      context.forward(
          new Record<>(
              userTaskResponseTriggerRecord.value().getProcessInstanceId(),
              userTaskResponseTriggerRecord.value(),
              clock.millis()));
    } catch (Exception e) {
      log.error(
          "⚠ Exception processing usertasks-response record, routing to DLQ: {}",
          e.getMessage(),
          e);
      emitUserTaskResponseDlq(
          userTaskResponseTriggerRecord, "PROCESSOR_EXCEPTION", e.getMessage(), "PROCESSOR");
    }
  }

  private boolean shouldBlockProtectedDataPlane(
      Record<UUID, UserTaskResponseTriggerDTO> userTaskResponseTriggerRecord) {
    if (protectedDataPlaneParticipationGuard == null) {
      return false;
    }
    ProtectedDataPlaneParticipationGuard.Decision decision =
        protectedDataPlaneParticipationGuard.evaluate();
    if (decision.permitted()) {
      return false;
    }
    emitUserTaskResponseDlq(
        userTaskResponseTriggerRecord, decision.reasonHint(), decision.reasonText(), "PROCESSOR");
    return true;
  }

  private void emitUserTaskResponseDlq(
      Record<UUID, UserTaskResponseTriggerDTO> userTaskResponseTriggerRecord,
      String reasonHint,
      String reasonText,
      String captureStage) {
    Map<String, byte[]> headersMap = headersToMap(userTaskResponseTriggerRecord.headers());
    headersMap.put(DLQ_REASON_HINT_HEADER, reasonHint.getBytes(StandardCharsets.UTF_8));
    headersMap.put(DLQ_REASON_TEXT_HEADER, reasonText.getBytes(StandardCharsets.UTF_8));
    headersMap.put(DLQ_CAPTURE_STAGE_HEADER, captureStage.getBytes(StandardCharsets.UTF_8));
    UserTaskResponseDlqEntryDTO dlqEntry =
        new UserTaskResponseDlqEntryDTO(
            userTaskResponseTriggerRecord.value() != null
                ? userTaskResponseTriggerRecord.value().getProcessInstanceId()
                : userTaskResponseTriggerRecord.key(),
            userTaskResponseTriggerRecord.value(),
            headersMap);
    context.forward(new Record<>(null, dlqEntry, clock.millis()));
  }

  private static Map<String, byte[]> headersToMap(org.apache.kafka.common.header.Headers headers) {
    if (headers == null) {
      return new HashMap<>();
    }
    return Arrays.stream(headers.toArray()).collect(Collectors.toMap(Header::key, Header::value));
  }
}
