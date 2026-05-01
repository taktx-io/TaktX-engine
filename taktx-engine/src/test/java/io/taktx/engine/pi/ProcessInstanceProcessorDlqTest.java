/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.engine.pi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.taktx.dto.ProcessDefinitionKey;
import io.taktx.dto.ProcessInstanceDTO;
import io.taktx.dto.ProcessInstanceDlqEntryDTO;
import io.taktx.dto.StartCommandDTO;
import io.taktx.dto.VariablesDTO;
import io.taktx.engine.config.TaktConfiguration;
import io.taktx.engine.pi.processor.IoMappingProcessor;
import io.taktx.engine.security.EngineAuthorizationService;
import io.taktx.engine.topicmanagement.DynamicTopicManager;
import io.taktx.security.AuthorizationTokenException;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.apache.kafka.streams.processor.api.ProcessorContext;
import org.apache.kafka.streams.processor.api.Record;
import org.apache.kafka.streams.state.KeyValueStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

@SuppressWarnings({"unchecked", "rawtypes"})
class ProcessInstanceProcessorDlqTest {

  private ProcessorContext<Object, Object> context;
  private KeyValueStore<UUID, ProcessInstanceDTO> processInstanceStore;
  private EngineAuthorizationService engineAuthorizationService;
  private ProcessInstanceProcessor processor;

  @BeforeEach
  void setUp() {
    DefinitionsCache definitionsCache = mock(DefinitionsCache.class);
    DefinitionMapper definitionMapper = mock(DefinitionMapper.class);
    ProcessInstanceMapper instanceMapper = mock(ProcessInstanceMapper.class);
    Forwarder forwarder = mock(Forwarder.class);
    IoMappingProcessor ioMappingProcessor = mock(IoMappingProcessor.class);
    TaktConfiguration taktConfiguration = mock(TaktConfiguration.class);
    ScopeProcessor scopeProcessor = mock(ScopeProcessor.class);
    Clock clock = Clock.fixed(Instant.ofEpochMilli(1_700_000_000_000L), ZoneOffset.UTC);
    DtoMapper dtoMapper = mock(DtoMapper.class);
    ProcessingStatistics processingStatistics = mock(ProcessingStatistics.class);
    DynamicTopicManager topicManager = mock(DynamicTopicManager.class);
    engineAuthorizationService = mock(EngineAuthorizationService.class);

    context = mock(ProcessorContext.class);
    processInstanceStore = mock(KeyValueStore.class);

    when(processInstanceStore.get(any())).thenReturn(null);

    processor =
        new ProcessInstanceProcessor(
            definitionsCache,
            definitionMapper,
            instanceMapper,
            forwarder,
            ioMappingProcessor,
            taktConfiguration,
            scopeProcessor,
            clock,
            dtoMapper,
            processingStatistics,
            topicManager,
            engineAuthorizationService);
    setField(processor, "context", context);
    setField(processor, "processInstanceStore", processInstanceStore);
  }

  private static void setField(Object target, String fieldName, Object value) {
    try {
      Field field = target.getClass().getDeclaredField(fieldName);
      field.setAccessible(true);
      field.set(target, value);
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException("Unable to set field '" + fieldName + "'", e);
    }
  }

  @Test
  void process_authorizationFailure_emitsDlqEntryWithAuthorizationHint() {
    UUID processInstanceId = UUID.randomUUID();
    byte[] payload = new byte[] {1, 2, 3};
    RecordHeaders headers = new RecordHeaders();
    headers.add("X-TaktX-Authorization", "jwt-token".getBytes(StandardCharsets.UTF_8));
    StartCommandDTO trigger =
        new StartCommandDTO(
            processInstanceId,
            null,
            null,
            new ProcessDefinitionKey("proc", -1),
            VariablesDTO.empty());
    ProcessInstanceTriggerEnvelope envelope =
        new ProcessInstanceTriggerEnvelope(payload, trigger, false, null);
    when(engineAuthorizationService.authorize(headers, envelope))
        .thenThrow(new AuthorizationTokenException("Entry command requires JWT"));

    processor.process(new Record<>(processInstanceId, envelope, 42L, headers));

    ArgumentCaptor<Record> recordCaptor = ArgumentCaptor.forClass(Record.class);
    verify(context).forward(recordCaptor.capture());
    Record forwarded = recordCaptor.getValue();
    assertThat(forwarded.key()).isNull();
    assertThat(forwarded.value()).isInstanceOf(ProcessInstanceDlqEntryDTO.class);

    ProcessInstanceDlqEntryDTO dlqEntry = (ProcessInstanceDlqEntryDTO) forwarded.value();
    assertThat(dlqEntry.getProcessInstanceId()).isEqualTo(processInstanceId);
    assertThat(dlqEntry.getData()).containsExactly(payload);
    assertThat(dlqEntry.getHeaders())
        .containsKey("X-TaktX-Authorization")
        .containsKey("X-TaktX-DLQ-Reason-Hint")
        .containsKey("X-TaktX-DLQ-Reason-Text")
        .containsKey("X-TaktX-DLQ-Capture-Stage");
    assertThat(
            new String(
                dlqEntry.getHeaders().get("X-TaktX-DLQ-Reason-Hint"), StandardCharsets.UTF_8))
        .isEqualTo("AUTHORIZATION_FAILED");
    assertThat(
            new String(
                dlqEntry.getHeaders().get("X-TaktX-DLQ-Capture-Stage"), StandardCharsets.UTF_8))
        .isEqualTo("PROCESSOR");
  }

  @Test
  void process_undecodableTriggerWithoutStoredInstance_stillEmitsDlqEntry() {
    UUID processInstanceId = UUID.randomUUID();
    byte[] payload = new byte[] {9, 8, 7};
    RecordHeaders headers = new RecordHeaders();
    headers.add("X-Test", "header".getBytes(StandardCharsets.UTF_8));
    ProcessInstanceTriggerEnvelope envelope =
        new ProcessInstanceTriggerEnvelope(payload, null, false, null, "decode failed");
    when(engineAuthorizationService.authorize(headers, envelope)).thenReturn(null);

    processor.process(new Record<>(processInstanceId, envelope, 99L, headers));

    ArgumentCaptor<Record> recordCaptor = ArgumentCaptor.forClass(Record.class);
    verify(context).forward(recordCaptor.capture());
    Record forwarded = recordCaptor.getValue();
    assertThat(forwarded.key()).isNull();
    assertThat(forwarded.value()).isInstanceOf(ProcessInstanceDlqEntryDTO.class);

    ProcessInstanceDlqEntryDTO dlqEntry = (ProcessInstanceDlqEntryDTO) forwarded.value();
    assertThat(dlqEntry.getProcessInstanceId()).isEqualTo(processInstanceId);
    assertThat(dlqEntry.getTrigger()).isNull();
    assertThat(dlqEntry.getData()).containsExactly(payload);
    assertThat(
            new String(
                dlqEntry.getHeaders().get("X-TaktX-DLQ-Reason-Hint"), StandardCharsets.UTF_8))
        .isEqualTo("CBOR_DECODE_ERROR");
    assertThat(
            new String(
                dlqEntry.getHeaders().get("X-TaktX-DLQ-Capture-Stage"), StandardCharsets.UTF_8))
        .isEqualTo("DESERIALIZER");
  }

  @Test
  void process_signatureFailure_emitsDlqEntryWithSignatureReasonHint() {
    UUID processInstanceId = UUID.randomUUID();
    byte[] payload = new byte[] {4, 5, 6};
    RecordHeaders headers = new RecordHeaders();
    headers.add("X-TaktX-Signature", "worker-key.AABB".getBytes(StandardCharsets.UTF_8));
    StartCommandDTO trigger =
        new StartCommandDTO(
            processInstanceId,
            null,
            null,
            new ProcessDefinitionKey("proc", -1),
            VariablesDTO.empty());
    ProcessInstanceTriggerEnvelope envelope =
        new ProcessInstanceTriggerEnvelope(
            payload,
            trigger,
            false,
            "worker-key",
            "Unknown or revoked signing keyId='worker-key' — treating as security violation");
    when(engineAuthorizationService.authorize(headers, envelope))
        .thenThrow(new AuthorizationTokenException("signature verification failed"));

    processor.process(new Record<>(processInstanceId, envelope, 77L, headers));

    ArgumentCaptor<Record> recordCaptor = ArgumentCaptor.forClass(Record.class);
    verify(context).forward(recordCaptor.capture());
    ProcessInstanceDlqEntryDTO dlqEntry =
        (ProcessInstanceDlqEntryDTO) recordCaptor.getValue().value();

    assertThat(
            new String(
                dlqEntry.getHeaders().get("X-TaktX-DLQ-Reason-Hint"), StandardCharsets.UTF_8))
        .isEqualTo("SIGNATURE_KEY_UNKNOWN");
    assertThat(
            new String(
                dlqEntry.getHeaders().get("X-TaktX-DLQ-Capture-Stage"), StandardCharsets.UTF_8))
        .isEqualTo("PROCESSOR");
  }
}
