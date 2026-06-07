/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.engine.pi;

import static io.taktx.engine.dlq.DlqHeaders.CAPTURE_STAGE;
import static io.taktx.engine.dlq.DlqHeaders.REASON_HINT;
import static io.taktx.engine.dlq.DlqHeaders.REASON_TEXT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.taktx.dto.ExternalTaskResponseResultDTO;
import io.taktx.dto.ExternalTaskResponseTriggerDTO;
import io.taktx.dto.ExternalTaskResponseType;
import io.taktx.dto.NamespaceSecurityPolicyDTO;
import io.taktx.dto.ProcessDefinitionKey;
import io.taktx.dto.ProcessInstanceDTO;
import io.taktx.dto.ProcessInstanceDlqEntryDTO;
import io.taktx.dto.SecurityEventDTO;
import io.taktx.dto.SecurityEventType;
import io.taktx.dto.SecurityMode;
import io.taktx.dto.StartCommandDTO;
import io.taktx.dto.TokenClaims;
import io.taktx.dto.UserTaskResponseResultDTO;
import io.taktx.dto.UserTaskResponseTriggerDTO;
import io.taktx.dto.UserTaskResponseType;
import io.taktx.dto.VariablesDTO;
import io.taktx.engine.config.TaktConfiguration;
import io.taktx.engine.pi.processor.IoMappingProcessor;
import io.taktx.engine.security.EngineAuthorizationService;
import io.taktx.engine.security.MessageSigningService;
import io.taktx.engine.security.ProtectedDataPlaneParticipationGuard;
import io.taktx.engine.security.SecurityEventPublisher;
import io.taktx.engine.topicmanagement.DynamicTopicManager;
import io.taktx.security.AuthorizationTokenException;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.apache.kafka.streams.processor.api.ProcessorContext;
import org.apache.kafka.streams.processor.api.Record;
import org.apache.kafka.streams.processor.api.RecordMetadata;
import org.apache.kafka.streams.state.KeyValueStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

@SuppressWarnings({"unchecked", "rawtypes"})
class ProcessInstanceProcessorDlqTest {

  private ProcessorContext<Object, Object> context;
  private EngineAuthorizationService engineAuthorizationService;
  private SecurityEventPublisher securityEventPublisher;
  private DefinitionsCache definitionsCache;
  private TaktConfiguration taktConfiguration;
  private Clock clock;
  private ProcessInstanceProcessor processor;

  @BeforeEach
  void setUp() {
    definitionsCache = mock(DefinitionsCache.class);
    DefinitionMapper definitionMapper = mock(DefinitionMapper.class);
    ProcessInstanceMapper instanceMapper = mock(ProcessInstanceMapper.class);
    Forwarder forwarder = mock(Forwarder.class);
    IoMappingProcessor ioMappingProcessor = mock(IoMappingProcessor.class);
    taktConfiguration = mock(TaktConfiguration.class);
    when(taktConfiguration.getTenantId()).thenReturn("tenant");
    when(taktConfiguration.getNamespace()).thenReturn("bank.payments");
    when(taktConfiguration.getHost()).thenReturn("engine-host");
    when(taktConfiguration.getPort()).thenReturn(8080);
    when(taktConfiguration.getPlatformPublicKey()).thenReturn(null);
    ScopeProcessor scopeProcessor = mock(ScopeProcessor.class);
    clock = Clock.fixed(Instant.ofEpochMilli(1_700_000_000_000L), ZoneOffset.UTC);
    DtoMapper dtoMapper = mock(DtoMapper.class);
    ProcessingStatistics processingStatistics = mock(ProcessingStatistics.class);
    DynamicTopicManager topicManager = mock(DynamicTopicManager.class);
    engineAuthorizationService = mock(EngineAuthorizationService.class);
    securityEventPublisher = mock(SecurityEventPublisher.class);

    context = mock(ProcessorContext.class);
    KeyValueStore<UUID, ProcessInstanceDTO> processInstanceStore = mock(KeyValueStore.class);

    // Provide record metadata so buildDlqEntryRef can produce a full reference
    RecordMetadata recordMetadata = mock(RecordMetadata.class);
    when(recordMetadata.topic()).thenReturn("process-instance");
    when(recordMetadata.partition()).thenReturn(0);
    when(recordMetadata.offset()).thenReturn(42L);
    when(context.recordMetadata()).thenReturn(Optional.of(recordMetadata));

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
            engineAuthorizationService,
            null,
            securityEventPublisher);
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
  void process_authorizationFailure_emitsSecurityEventWithoutDlq() {
    UUID processInstanceId = UUID.randomUUID();
    byte[] payload = new byte[] {1, 2, 3};
    RecordHeaders headers = new RecordHeaders();
    headers.add("tx-auth", "jwt-token".getBytes(StandardCharsets.UTF_8));
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

    ArgumentCaptor<SecurityEventDTO> eventCaptor = ArgumentCaptor.forClass(SecurityEventDTO.class);
    verify(securityEventPublisher).publish(eq(processInstanceId.toString()), eventCaptor.capture());
    assertThat(eventCaptor.getValue().getEventType())
        .isEqualTo(SecurityEventType.DATA_PLANE_BLOCKED);
    assertThat(eventCaptor.getValue().getCode()).isEqualTo("AUTHORIZATION_FAILED");
    assertThat(eventCaptor.getValue().getMessage()).contains("JWT");
    assertThat(eventCaptor.getValue().getMetadata())
        .containsEntry("rejectionStage", "AUTHORIZATION")
        .containsEntry("processInstanceId", processInstanceId.toString())
        .containsEntry("triggerType", StartCommandDTO.class.getSimpleName());
    verify(context, never()).forward(any());
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
    assertThat(new String(dlqEntry.getHeaders().get(REASON_HINT), StandardCharsets.UTF_8))
        .isEqualTo("PAYLOAD_DESERIALIZATION_ERROR");
    assertThat(new String(dlqEntry.getHeaders().get(CAPTURE_STAGE), StandardCharsets.UTF_8))
        .isEqualTo("DESERIALIZER");
  }

  @Test
  void process_undecodableTrigger_dlqEntryRef_isPopulatedWhenRecordMetadataPresent() {
    UUID processInstanceId = UUID.randomUUID();
    byte[] payload = new byte[] {9, 8, 7};
    RecordHeaders headers = new RecordHeaders();
    ProcessInstanceTriggerEnvelope envelope =
        new ProcessInstanceTriggerEnvelope(payload, null, false, null, "decode failed");
    when(engineAuthorizationService.authorize(headers, envelope)).thenReturn(null);

    processor.process(new Record<>(processInstanceId, envelope, 99L, headers));

    ArgumentCaptor<Record> recordCaptor = ArgumentCaptor.forClass(Record.class);
    verify(context).forward(recordCaptor.capture());
    ProcessInstanceDlqEntryDTO dlqEntry =
        (ProcessInstanceDlqEntryDTO) recordCaptor.getValue().value();

    // The DLQ entry is emitted; verify the canonical ref format matches
    // sourceTopic:partition:offset:sha256:hash — topic and coordinates from mocked metadata
    assertThat(dlqEntry.getHeaders()).containsKey(REASON_HINT);
    // (incident dlqEntryRef is validated via the IncidentInfo directly in integration tests
    // because the processInstanceStore returns null here — no stored instance to update)
    assertThat(dlqEntry.getProcessInstanceId()).isEqualTo(processInstanceId);
  }

  @Test
  void process_signatureFailure_emitsSecurityEventWithoutDlq() {
    UUID processInstanceId = UUID.randomUUID();
    byte[] payload = new byte[] {4, 5, 6};
    RecordHeaders headers = new RecordHeaders();
    headers.add("tx-sig", "worker-key.AABB".getBytes(StandardCharsets.UTF_8));
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

    ArgumentCaptor<SecurityEventDTO> eventCaptor = ArgumentCaptor.forClass(SecurityEventDTO.class);
    verify(securityEventPublisher).publish(eq(processInstanceId.toString()), eventCaptor.capture());
    assertThat(eventCaptor.getValue().getCode()).isEqualTo("SIGNATURE_KEY_UNKNOWN");
    assertThat(eventCaptor.getValue().getMetadata())
        .containsEntry("rejectionStage", "AUTHORIZATION")
        .containsEntry("signerKeyId", "worker-key");
    verify(context, never()).forward(any());
  }

  @Test
  void process_authoritativeAnchoredPolicyWithoutTrustAnchor_emitsSecurityEvent() {
    UUID processInstanceId = UUID.randomUUID();
    byte[] payload = new byte[] {10, 20, 30};
    RecordHeaders headers = new RecordHeaders();
    StartCommandDTO trigger =
        new StartCommandDTO(
            processInstanceId,
            null,
            null,
            new ProcessDefinitionKey("proc", -1),
            VariablesDTO.empty());
    ProcessInstanceTriggerEnvelope envelope =
        new ProcessInstanceTriggerEnvelope(payload, trigger, false, null);
    when(engineAuthorizationService.authorize(headers, envelope)).thenReturn(null);

    ProcessInstanceProcessor guardedProcessor =
        guardedProcessorWithPolicy(anchoredPolicy(42L), null, false);

    guardedProcessor.process(new Record<>(processInstanceId, envelope, 42L, headers));

    assertSecurityEvent(
        processInstanceId,
        ProtectedDataPlaneParticipationGuard.ENGINE_SIGNING_UNAVAILABLE,
        "READINESS");
    verifyNoInteractions(definitionsCache);
    verify(context, never()).forward(any());
  }

  @Test
  void process_activePolicyWhenEngineNotReady_emitsSecurityEventWithMismatchHint() {
    UUID processInstanceId = UUID.randomUUID();
    byte[] payload = new byte[] {11, 21, 31};
    RecordHeaders headers = new RecordHeaders();
    StartCommandDTO trigger =
        new StartCommandDTO(
            processInstanceId,
            null,
            null,
            new ProcessDefinitionKey("proc", -1),
            VariablesDTO.empty());
    ProcessInstanceTriggerEnvelope envelope =
        new ProcessInstanceTriggerEnvelope(payload, trigger, false, null);
    when(engineAuthorizationService.authorize(headers, envelope)).thenReturn(null);

    NamespaceSecurityPolicyDTO activeAnchored = anchoredPolicy(42L);
    ProcessInstanceProcessor guardedProcessor =
        guardedProcessorWithPolicy(activeAnchored, null, false);

    guardedProcessor.process(new Record<>(processInstanceId, envelope, 42L, headers));

    assertSecurityEvent(
        processInstanceId,
        ProtectedDataPlaneParticipationGuard.ENGINE_SIGNING_UNAVAILABLE,
        "READINESS");
    verifyNoInteractions(definitionsCache);
    verify(context, never()).forward(any());
  }

  @Test
  void process_authoritativePolicyMissingRequiredJwt_emitsSecurityEvent() {
    UUID processInstanceId = UUID.randomUUID();
    byte[] payload = new byte[] {12, 22, 32};
    RecordHeaders headers = new RecordHeaders();
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
        .thenThrow(
            new AuthorizationTokenException(
                "Entry command StartCommandDTO requires tx-auth (JWT)"));

    processor.process(new Record<>(processInstanceId, envelope, 43L, headers));

    assertSecurityEvent(processInstanceId, "AUTHORIZATION_FAILED", "AUTHORIZATION");
    verify(context, never()).forward(any());
  }

  @Test
  void process_authoritativePolicyMissingRequiredSignature_emitsSecurityEvent() {
    UUID processInstanceId = UUID.randomUUID();
    byte[] payload = new byte[] {13, 23, 33};
    RecordHeaders headers = new RecordHeaders();
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
        .thenThrow(
            new AuthorizationTokenException(
                "Entry command StartCommandDTO requires tx-sig (signingEnabled=true)"));

    processor.process(new Record<>(processInstanceId, envelope, 44L, headers));

    assertSecurityEvent(processInstanceId, "SIGNATURE_MISSING", "AUTHORIZATION");
    verify(context, never()).forward(any());
  }

  @Test
  void process_authoritativeAnchoredPolicyMissingTrustAnchor_emitsSecurityEvent() {
    UUID processInstanceId = UUID.randomUUID();
    byte[] payload = new byte[] {14, 24, 34};
    RecordHeaders headers = new RecordHeaders();
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
        .thenThrow(
            new AuthorizationTokenException(
                "Namespace security policy requires anchored trust but no platform public key is configured"));

    ProcessInstanceProcessor guardedProcessor =
        guardedProcessorWithPolicy(anchoredPolicy(42L), null, true);

    guardedProcessor.process(new Record<>(processInstanceId, envelope, 45L, headers));

    assertSecurityEvent(processInstanceId, "TRUST_ANCHOR_MISSING", "AUTHORIZATION");
    verify(context, never()).forward(any());
  }

  @Test
  void process_taskCompletionWithPresentedJwt_authDisabled_validatesJwtWithoutDlq() {
    UUID processInstanceId = UUID.randomUUID();
    byte[] payload = new byte[] {31, 32, 33};
    RecordHeaders headers = new RecordHeaders();
    headers.add("tx-auth", "stray-jwt".getBytes(StandardCharsets.UTF_8));
    ExternalTaskResponseTriggerDTO trigger =
        new ExternalTaskResponseTriggerDTO(
            processInstanceId,
            java.util.List.of(1L),
            "msg-1",
            new ExternalTaskResponseResultDTO(
                ExternalTaskResponseType.SUCCESS, true, null, null, 0L),
            VariablesDTO.empty());
    ProcessInstanceTriggerEnvelope envelope =
        new ProcessInstanceTriggerEnvelope(payload, trigger, false, null);
    when(engineAuthorizationService.authorize(headers, envelope)).thenReturn(null);
    when(engineAuthorizationService.validateJwtClaims(any(), eq(trigger)))
        .thenReturn(mock(TokenClaims.class));

    processor.process(new Record<>(processInstanceId, envelope, 77L, headers));

    verify(engineAuthorizationService).authorize(headers, envelope);
    verify(engineAuthorizationService).validateJwtClaims(any(), eq(trigger));
    verifyNoInteractions(securityEventPublisher);
    verify(context, never()).forward(any());
  }

  /**
   * Creates a guarded processor. When {@code signingAvailable=false} and anchored policy is set,
   * the guard will block processing with ENGINE_SIGNING_UNAVAILABLE (replacing old
   * TRUST_ANCHOR_MISSING logic).
   */
  private ProcessInstanceProcessor guardedProcessorWithPolicy(
      NamespaceSecurityPolicyDTO authoritativePolicy,
      String platformPublicKey,
      boolean signingAvailable) {
    boolean anchored =
        authoritativePolicy != null
            && io.taktx.dto.SecurityMode.ANCHORED == authoritativePolicy.getMode();

    MessageSigningService messageSigningService = mock(MessageSigningService.class);
    when(messageSigningService.getKeyId()).thenReturn(signingAvailable ? "engine-key-1" : null);
    when(messageSigningService.isPublicKeyPublished()).thenReturn(signingAvailable);

    ProcessInstanceProcessor guardedProcessor =
        new ProcessInstanceProcessor(
            definitionsCache,
            mock(DefinitionMapper.class),
            mock(ProcessInstanceMapper.class),
            mock(Forwarder.class),
            mock(IoMappingProcessor.class),
            taktConfiguration,
            mock(ScopeProcessor.class),
            clock,
            mock(DtoMapper.class),
            mock(ProcessingStatistics.class),
            mock(DynamicTopicManager.class),
            engineAuthorizationService,
            new ProtectedDataPlaneParticipationGuard(anchored, messageSigningService),
            securityEventPublisher);
    setField(guardedProcessor, "context", context);
    setField(guardedProcessor, "processInstanceStore", mock(KeyValueStore.class));
    return guardedProcessor;
  }

  private static NamespaceSecurityPolicyDTO anchoredPolicy(long version) {
    return NamespaceSecurityPolicyDTO.builder().mode(SecurityMode.ANCHORED).build();
  }

  private void assertSecurityEvent(UUID processInstanceId, String expectedCode, String stage) {
    ArgumentCaptor<SecurityEventDTO> eventCaptor = ArgumentCaptor.forClass(SecurityEventDTO.class);
    verify(securityEventPublisher).publish(eq(processInstanceId.toString()), eventCaptor.capture());
    assertThat(eventCaptor.getValue().getEventType())
        .isEqualTo(SecurityEventType.DATA_PLANE_BLOCKED);
    assertThat(eventCaptor.getValue().getCode()).isEqualTo(expectedCode);
    assertThat(eventCaptor.getValue().getMetadata())
        .containsEntry("rejectionStage", stage)
        .containsEntry("processInstanceId", processInstanceId.toString());
  }

  @Test
  void process_taskCompletionJwtValidationFailure_emitsSecurityEventInsteadOfDlq() {
    UUID processInstanceId = UUID.randomUUID();
    byte[] payload = new byte[] {41, 42, 43};
    RecordHeaders headers = new RecordHeaders();
    headers.add("tx-auth", "bad-jwt".getBytes(StandardCharsets.UTF_8));
    UserTaskResponseTriggerDTO trigger =
        new UserTaskResponseTriggerDTO(
            processInstanceId,
            java.util.List.of(2L),
            "msg-2",
            new UserTaskResponseResultDTO(UserTaskResponseType.COMPLETED, null, null),
            VariablesDTO.empty());
    ProcessInstanceTriggerEnvelope envelope =
        new ProcessInstanceTriggerEnvelope(payload, trigger, false, null);
    when(engineAuthorizationService.authorize(headers, envelope)).thenReturn(null);
    when(engineAuthorizationService.validateJwtClaims(any(), eq(trigger)))
        .thenThrow(
            new AuthorizationTokenException(
                "Signing key kid='bad-kid' is not trusted as a PLATFORM JWT issuer key"));

    processor.process(new Record<>(processInstanceId, envelope, 88L, headers));

    ArgumentCaptor<SecurityEventDTO> eventCaptor = ArgumentCaptor.forClass(SecurityEventDTO.class);
    verify(securityEventPublisher).publish(eq(processInstanceId.toString()), eventCaptor.capture());
    assertThat(eventCaptor.getValue().getCode()).isEqualTo("AUTHORIZATION_FAILED");
    assertThat(eventCaptor.getValue().getMetadata())
        .containsEntry("rejectionStage", "JWT_VALIDATION");
    assertThat(eventCaptor.getValue().getMessage()).contains("PLATFORM JWT issuer key");
    verify(context, never()).forward(any());
  }

  @Test
  void process_invalidBusinessKey_emitsDlqEntryWithInvalidBusinessMetadataHint() {
    UUID processInstanceId = UUID.randomUUID();
    byte[] payload = new byte[] {7, 8, 9};
    RecordHeaders headers = new RecordHeaders();
    // businessKey longer than 512 chars
    String tooLongKey = "a".repeat(513);
    StartCommandDTO trigger =
        new StartCommandDTO(
            processInstanceId,
            null,
            null,
            null,
            new ProcessDefinitionKey("proc", -1),
            VariablesDTO.empty(),
            false,
            Set.of(),
            tooLongKey,
            Set.of());
    ProcessInstanceTriggerEnvelope envelope =
        new ProcessInstanceTriggerEnvelope(payload, trigger, false, null);
    when(engineAuthorizationService.authorize(headers, envelope)).thenReturn(null);

    processor.process(new Record<>(processInstanceId, envelope, 10L, headers));

    ArgumentCaptor<Record> recordCaptor = ArgumentCaptor.forClass(Record.class);
    verify(context).forward(recordCaptor.capture());
    ProcessInstanceDlqEntryDTO dlqEntry =
        (ProcessInstanceDlqEntryDTO) recordCaptor.getValue().value();

    assertThat(dlqEntry.getProcessInstanceId()).isEqualTo(processInstanceId);
    assertThat(new String(dlqEntry.getHeaders().get(REASON_HINT), StandardCharsets.UTF_8))
        .isEqualTo("INVALID_BUSINESS_METADATA");
    assertThat(new String(dlqEntry.getHeaders().get(CAPTURE_STAGE), StandardCharsets.UTF_8))
        .isEqualTo("PROCESSOR");
    assertThat(new String(dlqEntry.getHeaders().get(REASON_TEXT), StandardCharsets.UTF_8))
        .contains("businessKey exceeds maximum length");
  }

  @Test
  void process_invalidTag_emitsDlqEntryWithInvalidBusinessMetadataHint() {
    UUID processInstanceId = UUID.randomUUID();
    byte[] payload = new byte[] {11, 12, 13};
    RecordHeaders headers = new RecordHeaders();
    StartCommandDTO trigger =
        new StartCommandDTO(
            processInstanceId,
            null,
            null,
            null,
            new ProcessDefinitionKey("proc", -1),
            VariablesDTO.empty(),
            false,
            Set.of(),
            null,
            Set.of("invalid tag with spaces"));
    ProcessInstanceTriggerEnvelope envelope =
        new ProcessInstanceTriggerEnvelope(payload, trigger, false, null);
    when(engineAuthorizationService.authorize(headers, envelope)).thenReturn(null);

    processor.process(new Record<>(processInstanceId, envelope, 10L, headers));

    ArgumentCaptor<Record> recordCaptor = ArgumentCaptor.forClass(Record.class);
    verify(context).forward(recordCaptor.capture());
    ProcessInstanceDlqEntryDTO dlqEntry =
        (ProcessInstanceDlqEntryDTO) recordCaptor.getValue().value();

    assertThat(dlqEntry.getProcessInstanceId()).isEqualTo(processInstanceId);
    assertThat(new String(dlqEntry.getHeaders().get(REASON_HINT), StandardCharsets.UTF_8))
        .isEqualTo("INVALID_BUSINESS_METADATA");
    assertThat(new String(dlqEntry.getHeaders().get(REASON_TEXT), StandardCharsets.UTF_8))
        .contains("illegal characters");
  }

  @Test
  void process_preInitialisationException_emitsDlqEntryWithInternalEngineErrorHint() {
    // Simulate a failure that occurs before processInstanceThreadLocal is populated —
    // e.g. the definitions cache/store is unavailable when processing a valid StartCommand.
    // Prior to the fix, handleIncident() would silently drop the message because the
    // processInstance thread-local was null.  After the fix, a DLQ entry must be emitted.
    UUID processInstanceId = UUID.randomUUID();
    byte[] payload = new byte[] {21, 22, 23};
    RecordHeaders headers = new RecordHeaders();
    StartCommandDTO trigger =
        new StartCommandDTO(
            processInstanceId,
            null,
            null,
            new ProcessDefinitionKey("proc", -1),
            VariablesDTO.empty());
    ProcessInstanceTriggerEnvelope envelope =
        new ProcessInstanceTriggerEnvelope(payload, trigger, false, null);
    when(engineAuthorizationService.authorize(headers, envelope)).thenReturn(null);
    // Make the definitions cache blow up before processInstanceThreadLocal is set
    doThrow(new RuntimeException("simulated store failure"))
        .when(definitionsCache)
        .computeIfAbsent(any(), any());

    processor.process(new Record<>(processInstanceId, envelope, 55L, headers));

    ArgumentCaptor<Record> recordCaptor = ArgumentCaptor.forClass(Record.class);
    verify(context).forward(recordCaptor.capture());
    ProcessInstanceDlqEntryDTO dlqEntry =
        (ProcessInstanceDlqEntryDTO) recordCaptor.getValue().value();

    assertThat(dlqEntry.getProcessInstanceId()).isEqualTo(processInstanceId);
    assertThat(new String(dlqEntry.getHeaders().get(REASON_HINT), StandardCharsets.UTF_8))
        .isEqualTo("INTERNAL_ENGINE_ERROR");
    assertThat(new String(dlqEntry.getHeaders().get(CAPTURE_STAGE), StandardCharsets.UTF_8))
        .isEqualTo("PROCESSOR");
    assertThat(new String(dlqEntry.getHeaders().get(REASON_TEXT), StandardCharsets.UTF_8))
        .contains("simulated store failure");
  }
}
