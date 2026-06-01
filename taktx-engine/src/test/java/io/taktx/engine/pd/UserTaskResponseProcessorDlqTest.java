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
import static org.mockito.Mockito.when;

import io.taktx.dto.NamespaceSecurityPolicyDTO;
import io.taktx.dto.ProcessInstanceTriggerDTO;
import io.taktx.dto.ProcessDefinitionKey;
import io.taktx.dto.SecurityMode;
import io.taktx.dto.StartCommandDTO;
import io.taktx.dto.UserTaskResponseDlqEntryDTO;
import io.taktx.dto.UserTaskResponseResultDTO;
import io.taktx.dto.UserTaskResponseTriggerDTO;
import io.taktx.dto.UserTaskResponseType;
import io.taktx.dto.VariablesDTO;
import io.taktx.engine.config.NamespaceSecurityPolicyStore;
import io.taktx.engine.config.TaktConfiguration;
import io.taktx.engine.pi.ProcessInstanceTriggerEnvelope;
import io.taktx.engine.security.EngineSecurityReadinessEvaluator;
import io.taktx.engine.security.MessageSigningService;
import io.taktx.engine.security.ProtectedDataPlaneParticipationGuard;
import io.taktx.security.NamespaceSecurityPolicySupport;
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
  private TaktConfiguration configuration;
  private UserTaskResponseProcessor processor;
  private final Clock clock = Clock.fixed(Instant.ofEpochMilli(1_700_000_000_000L), ZoneOffset.UTC);

  @BeforeEach
  void setUp() {
    context = mock(ProcessorContext.class);
    configuration = mock(TaktConfiguration.class);
    when(configuration.getTenantId()).thenReturn("tenant");
    when(configuration.getNamespace()).thenReturn("bank.payments");
    when(configuration.getHost()).thenReturn("engine-host");
    when(configuration.getPort()).thenReturn(8080);
    when(configuration.getSigningIdentitySourceType()).thenReturn("file");
    when(configuration.getEngineKeyRegistrationSignature()).thenReturn("engine-registration-signature");
    when(configuration.getPlatformPublicKey()).thenReturn(null);
    processor = new UserTaskResponseProcessor(clock);
    processor.init(context);
  }

  @Test
  void process_nullValue_emitsDlqWithDecodeErrorHint() {
    UUID processInstanceId = UUID.randomUUID();
    RecordHeaders headers = new RecordHeaders();
    headers.add("X-Token", "tok".getBytes(StandardCharsets.UTF_8));
    Record<UUID, ProcessInstanceTriggerEnvelope> userTaskResponseTriggerRecord =
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
        .isEqualTo("PAYLOAD_DESERIALIZATION_ERROR");
    assertThat(new String(dlqEntry.getHeaders().get(CAPTURE_STAGE), StandardCharsets.UTF_8))
        .isEqualTo("DESERIALIZER");
  }

  @Test
  void process_validResponse_forwardsToProcessInstanceTrigger() {
    UUID processInstanceId = UUID.randomUUID();
    UserTaskResponseResultDTO result =
        new UserTaskResponseResultDTO(UserTaskResponseType.COMPLETED, null, null);
    UserTaskResponseTriggerDTO response = userTaskResponse(processInstanceId, List.of(1L), result);

    RecordHeaders headers = new RecordHeaders();
    headers.add("tx-sig", "user-key.AABB".getBytes(StandardCharsets.UTF_8));
    ProcessInstanceTriggerEnvelope envelope =
        new ProcessInstanceTriggerEnvelope(new byte[] {1, 2, 3}, response, true, "user-key")
            .withReplayRoutingKeyHint("issuer:audit-1");
    Record<UUID, ProcessInstanceTriggerEnvelope> userTaskResponseTriggerRecord =
        new Record<>(processInstanceId, envelope, 200L, headers);

    processor.process(userTaskResponseTriggerRecord);

    ArgumentCaptor<Record> captor = ArgumentCaptor.forClass(Record.class);
    verify(context).forward(captor.capture());
    Record forwarded = captor.getValue();
    assertThat(forwarded.key()).isEqualTo(processInstanceId);
    assertThat(forwarded.value()).isInstanceOf(ProcessInstanceTriggerEnvelope.class);
    assertThat(forwarded.value()).isSameAs(envelope);
    assertThat(new String(forwarded.headers().lastHeader("tx-sig").value(), StandardCharsets.UTF_8))
        .isEqualTo("user-key.AABB");
  }

  @Test
  void process_forwardThrows_emitsDlqWithProcessorExceptionHint() {
    UUID processInstanceId = UUID.randomUUID();
    UserTaskResponseResultDTO result =
        new UserTaskResponseResultDTO(UserTaskResponseType.COMPLETED, null, null);
    UserTaskResponseTriggerDTO response = userTaskResponse(processInstanceId, List.of(2L), result);

    // The first forward call (with ProcessInstanceTriggerEnvelope) throws; the second (DLQ) succeeds.
    doThrow(new RuntimeException("forward failed"))
        .doNothing()
        .when(context)
        .forward(org.mockito.ArgumentMatchers.any());

    RecordHeaders headers = new RecordHeaders();
    Record<UUID, ProcessInstanceTriggerEnvelope> userTaskResponseTriggerRecord =
        new Record<>(
            processInstanceId,
            new ProcessInstanceTriggerEnvelope(new byte[] {4, 5, 6}, response, false, null),
            300L,
            headers);

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

  @Test
  void process_authoritativeAnchoredPolicyWithoutTrustAnchor_emitsDlq() {
    UUID processInstanceId = UUID.randomUUID();
    UserTaskResponseResultDTO result =
        new UserTaskResponseResultDTO(UserTaskResponseType.COMPLETED, null, null);
    UserTaskResponseTriggerDTO response = userTaskResponse(processInstanceId, List.of(3L), result);
    UserTaskResponseProcessor guardedProcessor =
        guardedProcessorWithPolicy(anchoredPolicy(42L));

    guardedProcessor.process(
        new Record<>(
            processInstanceId,
            new ProcessInstanceTriggerEnvelope(new byte[] {7, 8, 9}, response, false, null),
            400L,
            new RecordHeaders()));

    ArgumentCaptor<Record> captor = ArgumentCaptor.forClass(Record.class);
    verify(context).forward(captor.capture());
    Record forwarded = captor.getValue();
    assertThat(forwarded.key()).isNull();
    assertThat(forwarded.value()).isInstanceOf(UserTaskResponseDlqEntryDTO.class);
    UserTaskResponseDlqEntryDTO dlqEntry = (UserTaskResponseDlqEntryDTO) forwarded.value();
    assertThat(new String(dlqEntry.getHeaders().get(REASON_HINT), StandardCharsets.UTF_8))
        .isEqualTo("TRUST_ANCHOR_MISSING");
    assertThat(new String(dlqEntry.getHeaders().get(REASON_TEXT), StandardCharsets.UTF_8))
        .contains("platform public key");
  }

  @Test
  void process_noExplicitPolicy_stillForwardsNormally() {
    UUID processInstanceId = UUID.randomUUID();
    UserTaskResponseResultDTO result =
        new UserTaskResponseResultDTO(UserTaskResponseType.COMPLETED, null, null);
    UserTaskResponseTriggerDTO response = userTaskResponse(processInstanceId, List.of(4L), result);
    UserTaskResponseProcessor guardedProcessor = guardedProcessorWithPolicy(null);
    ProcessInstanceTriggerEnvelope envelope =
        new ProcessInstanceTriggerEnvelope(new byte[] {10, 11}, response, true, "user-key");

    guardedProcessor.process(new Record<>(processInstanceId, envelope, 500L, new RecordHeaders()));

    ArgumentCaptor<Record> captor = ArgumentCaptor.forClass(Record.class);
    verify(context).forward(captor.capture());
    Record forwarded = captor.getValue();
    assertThat(forwarded.key()).isEqualTo(processInstanceId);
    assertThat(forwarded.value()).isSameAs(envelope);
  }

  @Test
  void process_wrongTriggerType_emitsPayloadTypeMismatchDlq() {
    UUID processInstanceId = UUID.randomUUID();
    StartCommandDTO wrongTrigger =
        new StartCommandDTO(
            processInstanceId, null, null, new ProcessDefinitionKey("proc", 1), VariablesDTO.empty());

    processor.process(
        new Record<>(
            processInstanceId,
            new ProcessInstanceTriggerEnvelope(new byte[] {12, 13}, wrongTrigger, false, null),
            600L,
            new RecordHeaders()));

    ArgumentCaptor<Record> captor = ArgumentCaptor.forClass(Record.class);
    verify(context).forward(captor.capture());
    UserTaskResponseDlqEntryDTO dlqEntry = (UserTaskResponseDlqEntryDTO) captor.getValue().value();
    assertThat(new String(dlqEntry.getHeaders().get(REASON_HINT), StandardCharsets.UTF_8))
        .isEqualTo("PAYLOAD_TYPE_MISMATCH");
    assertThat(new String(dlqEntry.getHeaders().get(REASON_TEXT), StandardCharsets.UTF_8))
        .contains("Expected UserTaskResponseTriggerDTO but decoded StartCommandDTO");
  }

  private static UserTaskResponseTriggerDTO userTaskResponse(
      UUID processInstanceId, List<Long> path, UserTaskResponseResultDTO result) {
    return new UserTaskResponseTriggerDTO(processInstanceId, path, result, VariablesDTO.empty());
  }

  private UserTaskResponseProcessor guardedProcessorWithPolicy(
      NamespaceSecurityPolicyDTO authoritativePolicy) {
    NamespaceSecurityPolicyStore policyStore = new NamespaceSecurityPolicyStore();
    policyStore.update(authoritativePolicy);
    MessageSigningService messageSigningService = mock(MessageSigningService.class);
    when(messageSigningService.getKeyId()).thenReturn("engine-key-1");
    when(messageSigningService.isPublicKeyPublished()).thenReturn(true);

    UserTaskResponseProcessor guardedProcessor =
        new UserTaskResponseProcessor(
            clock,
            new ProtectedDataPlaneParticipationGuard(
                policyStore,
                new EngineSecurityReadinessEvaluator(
                    configuration, policyStore, messageSigningService, clock),
                clock));
    guardedProcessor.init(context);
    return guardedProcessor;
  }

  private static NamespaceSecurityPolicyDTO anchoredPolicy(long version) {
    return NamespaceSecurityPolicySupport.requireValid(
        NamespaceSecurityPolicyDTO.builder()
            .mode(SecurityMode.ANCHORED)
            .policyVersion(version)
            .build());
  }
}
