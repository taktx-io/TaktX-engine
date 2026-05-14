/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.engine.pd;

import static io.taktx.engine.dlq.DlqHeaders.CAPTURE_STAGE;
import static io.taktx.engine.dlq.DlqHeaders.REASON_HINT;
import static io.taktx.engine.dlq.DlqHeaders.REASON_TEXT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.taktx.dto.ProcessInstanceTriggerDTO;
import io.taktx.dto.UserTaskResponseDlqEntryDTO;
import io.taktx.dto.UserTaskResponseResultDTO;
import io.taktx.dto.UserTaskResponseTriggerDTO;
import io.taktx.dto.UserTaskResponseType;
import io.taktx.dto.VariablesDTO;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.apache.kafka.streams.processor.api.ProcessorContext;
import org.apache.kafka.streams.processor.api.Record;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

@SuppressWarnings({"unchecked", "rawtypes"})
class UserTaskResponseProcessorDlqTest {

  private ProcessorContext<Object, Object> context;
  private UserTaskResponseProcessor processor;
  private final Clock clock = Clock.fixed(Instant.ofEpochMilli(1_700_000_000_000L), ZoneOffset.UTC);

  @BeforeEach
  void setUp() {
    context = mock(ProcessorContext.class);
    processor = new UserTaskResponseProcessor(clock);
    processor.init(context);
  }

  @Test
  void process_nullValue_emitsDlqWithDecodeErrorHint() {
    UUID processInstanceId = UUID.randomUUID();
    RecordHeaders headers = new RecordHeaders();
    headers.add("X-Token", "tok".getBytes(StandardCharsets.UTF_8));
    Record<UUID, UserTaskResponseTriggerDTO> userTaskResponseTriggerRecord =
        new Record<>(processInstanceId, null, 100L, headers);

    processor.process(userTaskResponseTriggerRecord);

    ArgumentCaptor<Record> captor = ArgumentCaptor.forClass(Record.class);
    verify(context).forward(captor.capture());
    Record forwarded = captor.getValue();
    assertThat(forwarded.key()).isNull();
    assertThat(forwarded.value()).isInstanceOf(UserTaskResponseDlqEntryDTO.class);

    UserTaskResponseDlqEntryDTO dlqEntry = (UserTaskResponseDlqEntryDTO) forwarded.value();
    assertThat(dlqEntry.getProcessInstanceId()).isEqualTo(processInstanceId);
    assertThat(dlqEntry.getValue()).isNull();
    assertThat(new String(dlqEntry.getHeaders().get(REASON_HINT), StandardCharsets.UTF_8))
        .isEqualTo("CBOR_DECODE_ERROR");
    assertThat(new String(dlqEntry.getHeaders().get(CAPTURE_STAGE), StandardCharsets.UTF_8))
        .isEqualTo("DESERIALIZER");
  }

  @Test
  void process_validResponse_forwardsToProcessInstanceTrigger() {
    UUID processInstanceId = UUID.randomUUID();
    UserTaskResponseResultDTO result =
        new UserTaskResponseResultDTO(UserTaskResponseType.COMPLETED, null, null);
    UserTaskResponseTriggerDTO response =
        new UserTaskResponseTriggerDTO(
            processInstanceId, List.of(1L), result, VariablesDTO.empty());

    RecordHeaders headers = new RecordHeaders();
    Record<UUID, UserTaskResponseTriggerDTO> userTaskResponseTriggerRecord =
        new Record<>(processInstanceId, response, 200L, headers);

    processor.process(userTaskResponseTriggerRecord);

    ArgumentCaptor<Record> captor = ArgumentCaptor.forClass(Record.class);
    verify(context).forward(captor.capture());
    Record forwarded = captor.getValue();
    assertThat(forwarded.key()).isEqualTo(processInstanceId);
    assertThat(forwarded.value()).isInstanceOf(ProcessInstanceTriggerDTO.class);
    assertThat(forwarded.value()).isSameAs(response);
  }

  @Test
  void process_forwardThrows_emitsDlqWithProcessorExceptionHint() {
    UUID processInstanceId = UUID.randomUUID();
    UserTaskResponseResultDTO result =
        new UserTaskResponseResultDTO(UserTaskResponseType.COMPLETED, null, null);
    UserTaskResponseTriggerDTO response =
        new UserTaskResponseTriggerDTO(
            processInstanceId, List.of(2L), result, VariablesDTO.empty());

    // The first forward call (with ProcessInstanceTriggerDTO) throws; the second (DLQ) succeeds.
    doThrow(new RuntimeException("forward failed"))
        .doNothing()
        .when(context)
        .forward(org.mockito.ArgumentMatchers.any());

    RecordHeaders headers = new RecordHeaders();
    Record<UUID, UserTaskResponseTriggerDTO> userTaskResponseTriggerRecord =
        new Record<>(processInstanceId, response, 300L, headers);

    processor.process(userTaskResponseTriggerRecord);

    ArgumentCaptor<Record> captor = ArgumentCaptor.forClass(Record.class);
    verify(context, times(2)).forward(captor.capture());
    // The second forwarded record should be the DLQ entry
    Record forwarded = captor.getAllValues().get(1);
    assertThat(forwarded.value()).isInstanceOf(UserTaskResponseDlqEntryDTO.class);

    UserTaskResponseDlqEntryDTO dlqEntry = (UserTaskResponseDlqEntryDTO) forwarded.value();
    assertThat(new String(dlqEntry.getHeaders().get(REASON_HINT), StandardCharsets.UTF_8))
        .isEqualTo("PROCESSOR_EXCEPTION");
    assertThat(new String(dlqEntry.getHeaders().get(REASON_TEXT), StandardCharsets.UTF_8))
        .contains("forward failed");
  }
}
