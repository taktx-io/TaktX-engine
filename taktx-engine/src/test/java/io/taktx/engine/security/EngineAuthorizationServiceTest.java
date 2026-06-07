/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.engine.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.jsonwebtoken.Jwts;
import io.taktx.dto.AbortTriggerDTO;
import io.taktx.dto.CommandAuthMethod;
import io.taktx.dto.CommandTrustMetadataDTO;
import io.taktx.dto.CommandTrustVerificationResult;
import io.taktx.dto.ContinueFlowElementTriggerDTO;
import io.taktx.dto.CorrelationMessageEventTriggerDTO;
import io.taktx.dto.DefinitionMessageEventTriggerDTO;
import io.taktx.dto.DefinitionScheduleKeyDTO;
import io.taktx.dto.ExternalTaskResponseResultDTO;
import io.taktx.dto.ExternalTaskResponseTriggerDTO;
import io.taktx.dto.ExternalTaskResponseType;
import io.taktx.dto.GlobalConfigurationDTO;
import io.taktx.dto.KeyRole;
import io.taktx.dto.OneTimeScheduleDTO;
import io.taktx.dto.ProcessDefinitionKey;
import io.taktx.dto.ReplayProtectionMode;
import io.taktx.dto.SetVariableTriggerDTO;
import io.taktx.dto.SignalDTO;
import io.taktx.dto.SigningKeyDTO;
import io.taktx.dto.SigningKeyDTO.KeyStatus;
import io.taktx.dto.StartCommandDTO;
import io.taktx.dto.TimeBucket;
import io.taktx.dto.TopicMetaDTO;
import io.taktx.dto.UserTaskResponseResultDTO;
import io.taktx.dto.UserTaskResponseTriggerDTO;
import io.taktx.dto.UserTaskResponseType;
import io.taktx.dto.VariablesDTO;
import io.taktx.engine.config.GlobalConfigStore;
import io.taktx.engine.config.TaktConfiguration;
import io.taktx.engine.pd.MessageEventIngressEnvelope;
import io.taktx.engine.pd.ScheduleCommandEnvelope;
import io.taktx.engine.pd.SignalIngressEnvelope;
import io.taktx.engine.pi.ProcessInstanceTriggerEnvelope;
import io.taktx.engine.topicmanagement.TopicMetaIngressEnvelope;
import io.taktx.security.AuthorizationTokenException;
import java.nio.charset.StandardCharsets;
import java.security.KeyPairGenerator;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import org.apache.kafka.common.header.Headers;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.state.ReadOnlyKeyValueStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@SuppressWarnings("unchecked")
class EngineAuthorizationServiceTest {

  private static final String ISSUER = "taktx-platform-service";
  private static final String PLATFORM_KID = "platform-key-2025";

  private TaktConfiguration config;
  private GlobalConfigStore globalConfigStore;
  private PublicKeyProvider publicKeyProvider;
  private KafkaStreams kafkaStreams;
  private ReadOnlyKeyValueStore<String, SigningKeyDTO> signingKeysStore;
  private EngineAuthorizationService service;

  private java.security.KeyPair rsaKeyPair;

  @BeforeEach
  void setUp() throws Exception {
    rsaKeyPair = KeyPairGenerator.getInstance("RSA").generateKeyPair();

    config = mock(TaktConfiguration.class);
    when(config.isAnchored()).thenReturn(false);
    globalConfigStore = new GlobalConfigStore();
    publicKeyProvider = mock(PublicKeyProvider.class);
    kafkaStreams = mock(KafkaStreams.class);
    signingKeysStore = mock(ReadOnlyKeyValueStore.class);

    when(publicKeyProvider.getKey(PLATFORM_KID)).thenReturn(rsaKeyPair.getPublic());
    when(kafkaStreams.store(org.mockito.ArgumentMatchers.any())).thenReturn(signingKeysStore);
    when(config.getPrefixed(org.mockito.ArgumentMatchers.any()))
        .thenReturn("default.taktx-signing-keys");

    service =
        new EngineAuthorizationService(config, globalConfigStore, publicKeyProvider, kafkaStreams);
  }

  // ── authorization disabled ─────────────────────────────────────────────────

  @Test
  void disabled_returnsNull_forAnyCommand() {
    assertThat(service.authorize(new RecordHeaders(), envelope(startCommand("proc", -1)))).isNull();
  }

  @Test
  void disabled_withPresentedJwt_returnsJwtMetadataAsOptionalContext() {
    String auditId = UUID.randomUUID().toString();
    String jwt = buildJwt("START", "my-proc", -1, auditId, futureExpiry());

    CommandTrustMetadataDTO result =
        service.authorize(headersWithAuth(jwt), envelope(startCommand("my-proc", -1)));

    assertThat(result)
        .isEqualTo(
            CommandTrustMetadataDTO.builder()
                .authMethod(CommandAuthMethod.JWT)
                .verificationResult(CommandTrustVerificationResult.JWT_AUTHORIZED)
                .trusted(true)
                .userId("user-1")
                .issuer(ISSUER)
                .build());
  }

  // ── null trigger (payload deserialization failed) ─────────────────────────

  @Test
  void nullTrigger_securityDisabled_returnsNullWithoutThrowing() {
    // When payload deserialization fails, the envelope carries a null trigger.
    // authorize() must return null gracefully so that ProcessInstanceProcessor's
    // handleUnDecodedTrigger() can emit the PAYLOAD_DESERIALIZATION_ERROR DLQ entry.
    ProcessInstanceTriggerEnvelope nullTriggerEnvelope =
        new ProcessInstanceTriggerEnvelope(new byte[0], null, false, null);
    assertThat(service.authorize(new RecordHeaders(), nullTriggerEnvelope)).isNull();
  }

  @Test
  void nullTrigger_securityEnabled_returnsNullWithoutThrowing() {
    // Security active, but trigger is null — must still not throw NullPointerException.
    globalConfigStore.update(authorizationConfig());
    ProcessInstanceTriggerEnvelope nullTriggerEnvelope =
        new ProcessInstanceTriggerEnvelope(new byte[0], null, false, null);
    assertThat(service.authorize(new RecordHeaders(), nullTriggerEnvelope)).isNull();
  }

  // ── valid JWT token ────────────────────────────────────────────────────────

  @Test
  void validToken_start_returnsJwtMetadata() {
    globalConfigStore.update(authorizationConfig());

    String auditId = UUID.randomUUID().toString();
    String jwt = buildJwt("START", "my-proc", -1, auditId, futureExpiry());

    CommandTrustMetadataDTO result =
        service.authorize(headersWithAuth(jwt), envelope(startCommand("my-proc", -1)));
    assertThat(result)
        .isEqualTo(
            CommandTrustMetadataDTO.builder()
                .authMethod(CommandAuthMethod.JWT)
                .verificationResult(CommandTrustVerificationResult.JWT_AUTHORIZED)
                .trusted(true)
                .userId("user-1")
                .issuer(ISSUER)
                .build());
  }

  @Test
  void validToken_cancel_returnsJwtMetadata() {
    globalConfigStore.update(authorizationConfig());

    String auditId = UUID.randomUUID().toString();
    String jwt = buildJwt("CANCEL", null, -1, auditId, futureExpiry());
    AbortTriggerDTO cmd = new AbortTriggerDTO(UUID.randomUUID(), List.of());

    CommandTrustMetadataDTO result = service.authorize(headersWithAuth(jwt), envelope(cmd));
    assertThat(result.getAuthMethod()).isEqualTo(CommandAuthMethod.JWT);
    assertThat(result.getVerificationResult())
        .isEqualTo(CommandTrustVerificationResult.JWT_AUTHORIZED);
    assertThat(result.getTrusted()).isTrue();
    assertThat(result.getUserId()).isEqualTo("user-1");
    assertThat(result.getIssuer()).isEqualTo(ISSUER);
  }

  @Test
  void validToken_setVariable_returnsJwtMetadata() {
    globalConfigStore.update(authorizationConfig());

    String auditId = UUID.randomUUID().toString();
    String jwt = buildJwt("SET_VARIABLE", null, -1, auditId, futureExpiry());
    SetVariableTriggerDTO cmd = setVariableTrigger();

    CommandTrustMetadataDTO result = service.authorize(headersWithAuth(jwt), envelope(cmd));
    assertThat(result.getAuthMethod()).isEqualTo(CommandAuthMethod.JWT);
    assertThat(result.getVerificationResult())
        .isEqualTo(CommandTrustVerificationResult.JWT_AUTHORIZED);
    assertThat(result.getTrusted()).isTrue();
    assertThat(result.getUserId()).isEqualTo("user-1");
    assertThat(result.getIssuer()).isEqualTo(ISSUER);
  }

  @Test
  void validToken_userTaskCompletion_returnsJwtMetadata() {
    globalConfigStore.update(userTaskAuthorizationConfig(false));

    String jwt =
        buildJwt(
            "USER_TASK_COMPLETE", null, -1, UUID.randomUUID().toString(), "user-1", futureExpiry());

    CommandTrustMetadataDTO result =
        service.authorize(headersWithAuth(jwt), envelope(userTaskResponseTrigger()));

    assertThat(result.getAuthMethod()).isEqualTo(CommandAuthMethod.JWT);
    assertThat(result.getVerificationResult())
        .isEqualTo(CommandTrustVerificationResult.JWT_AUTHORIZED);
    assertThat(result.getTrusted()).isTrue();
    assertThat(result.getUserId()).isEqualTo("user-1");
    assertThat(result.getIssuer()).isEqualTo(ISSUER);
  }

  @Test
  void validToken_externalTaskCompletion_returnsJwtMetadata() {
    globalConfigStore.update(externalTaskAuthorizationConfig(false));

    String jwt =
        buildJwt(
            "EXTERNAL_TASK_COMPLETE",
            null,
            -1,
            UUID.randomUUID().toString(),
            "user-1",
            futureExpiry());

    CommandTrustMetadataDTO result =
        service.authorize(headersWithAuth(jwt), envelope(externalTaskResponseTrigger()));

    assertThat(result.getAuthMethod()).isEqualTo(CommandAuthMethod.JWT);
    assertThat(result.getVerificationResult())
        .isEqualTo(CommandTrustVerificationResult.JWT_AUTHORIZED);
    assertThat(result.getTrusted()).isTrue();
    assertThat(result.getUserId()).isEqualTo("user-1");
    assertThat(result.getIssuer()).isEqualTo(ISSUER);
  }

  @Test
  void validToken_userTaskCompletion_acceptsServiceAccountSubjectAsOpaqueString() {
    globalConfigStore.update(userTaskAuthorizationConfig(false));

    String jwt =
        buildJwt(
            "USER_TASK_COMPLETE",
            null,
            -1,
            UUID.randomUUID().toString(),
            "service-account://console/backend",
            futureExpiry());

    CommandTrustMetadataDTO result =
        service.authorize(headersWithAuth(jwt), envelope(userTaskResponseTrigger()));

    assertThat(result.getUserId()).isEqualTo("service-account://console/backend");
    assertThat(result.getIssuer()).isEqualTo(ISSUER);
  }

  @Test
  void topicMetaIngress_securityDisabled_returnsNullWithoutCheckingSignature() {
    // globalConfigStore has no config: both signingEnabled and engineRequiresAuthorization default
    // to false.  No signature header is present — the method must return null, not throw.
    TopicMetaDTO request =
        new TopicMetaDTO("tenant.ns.external-task-trigger-billing", 3, null, (short) 1);

    SigningKeyDTO result =
        service.authorizeTopicMetaIngress(
            new RecordHeaders(),
            new TopicMetaIngressEnvelope(new byte[0], request, false, null, null));

    assertThat(result).isNull();
  }

  @Test
  void topicMetaIngress_trustedClientKeyAccepted() {
    globalConfigStore.update(signingConfig());

    String keyId = "worker-topic-request-key";
    SigningKeyDTO keyEntry =
        SigningKeyDTO.builder()
            .keyId(keyId)
            .publicKeyBase64("dummy")
            .algorithm("Ed25519")
            .status(KeyStatus.ACTIVE)
            .role(KeyRole.CLIENT)
            .build();
    when(signingKeysStore.get(keyId)).thenReturn(keyEntry);

    TopicMetaDTO request =
        new TopicMetaDTO("tenant.ns.external-task-trigger-billing", 3, null, (short) 1);

    SigningKeyDTO result =
        service.authorizeTopicMetaIngress(
            headersWithSignature(keyId),
            new TopicMetaIngressEnvelope(new byte[0], request, true, keyId, null));

    assertThat(result).isEqualTo(keyEntry);
  }

  @Test
  void topicMetaIngress_missingSignatureRejected() {
    globalConfigStore.update(signingConfig());

    TopicMetaDTO request =
        new TopicMetaDTO("tenant.ns.external-task-trigger-billing", 3, null, (short) 1);

    assertThatThrownBy(
            () ->
                service.authorizeTopicMetaIngress(
                    new RecordHeaders(),
                    new TopicMetaIngressEnvelope(new byte[0], request, false, null, null)))
        .isInstanceOf(AuthorizationTokenException.class)
        .hasMessageContaining("tx-sig");
  }

  @Test
  void topicMetaIngress_revokedKeyRejected() {
    globalConfigStore.update(signingConfig());

    String keyId = "revoked-topic-request-key";
    SigningKeyDTO keyEntry =
        SigningKeyDTO.builder()
            .keyId(keyId)
            .publicKeyBase64("dummy")
            .algorithm("Ed25519")
            .status(KeyStatus.REVOKED)
            .role(KeyRole.CLIENT)
            .build();
    when(signingKeysStore.get(keyId)).thenReturn(keyEntry);

    TopicMetaDTO request =
        new TopicMetaDTO("tenant.ns.external-task-trigger-billing", 3, null, (short) 1);

    assertThatThrownBy(
            () ->
                service.authorizeTopicMetaIngress(
                    headersWithSignature(keyId),
                    new TopicMetaIngressEnvelope(new byte[0], request, true, keyId, null)))
        .isInstanceOf(AuthorizationTokenException.class)
        .hasMessageContaining("Revoked Ed25519 keyId");
  }

  @Test
  void topicMetaIngress_untrustedKeyRejected() {
    globalConfigStore.update(signingConfig());

    service =
        new EngineAuthorizationService(
            config, globalConfigStore, publicKeyProvider, kafkaStreams, (_, _) -> false);

    String keyId = "untrusted-topic-request-key";
    SigningKeyDTO keyEntry =
        SigningKeyDTO.builder()
            .keyId(keyId)
            .publicKeyBase64("dummy")
            .algorithm("Ed25519")
            .status(KeyStatus.ACTIVE)
            .role(KeyRole.CLIENT)
            .build();
    when(signingKeysStore.get(keyId)).thenReturn(keyEntry);

    TopicMetaDTO request =
        new TopicMetaDTO("tenant.ns.external-task-trigger-billing", 3, null, (short) 1);

    assertThatThrownBy(
            () ->
                service.authorizeTopicMetaIngress(
                    headersWithSignature(keyId),
                    new TopicMetaIngressEnvelope(new byte[0], request, true, keyId, null)))
        .isInstanceOf(AuthorizationTokenException.class)
        .hasMessageContaining("not trusted for required role CLIENT");
  }

  @Test
  void topicMetaIngress_signatureErrorRejected() {
    globalConfigStore.update(signingConfig());

    TopicMetaDTO request =
        new TopicMetaDTO("tenant.ns.external-task-trigger-billing", 3, null, (short) 1);

    assertThatThrownBy(
            () ->
                service.authorizeTopicMetaIngress(
                    headersWithSignature("broken-topic-request-key"),
                    new TopicMetaIngressEnvelope(
                        new byte[0],
                        request,
                        false,
                        "broken-topic-request-key",
                        "Malformed base64 signature for keyId=broken-topic-request-key: bad")))
        .isInstanceOf(AuthorizationTokenException.class)
        .hasMessageContaining("Malformed base64 signature");
  }

  @Test
  void scheduleCommand_securityDisabled_returnsNullWithoutCheckingSignature() {
    // globalConfigStore has no config: both signingEnabled and engineRequiresAuthorization default
    // to false.  No signature header is present — the method must return null, not throw.
    SigningKeyDTO result =
        service.authorizeScheduleCommand(
            scheduleKey(),
            new ScheduleCommandEnvelope(
                oneTimeSchedule(startCommand("proc", -1)), false, null, null));

    assertThat(result).isNull();
  }

  @Test
  void scheduleCommand_trustedEngineKeyAccepted() {
    globalConfigStore.update(signingConfig());

    String keyId = "engine-schedule-key";
    SigningKeyDTO keyEntry =
        SigningKeyDTO.builder()
            .keyId(keyId)
            .publicKeyBase64("dummy")
            .algorithm("Ed25519")
            .status(KeyStatus.ACTIVE)
            .role(KeyRole.ENGINE)
            .build();
    when(signingKeysStore.get(keyId)).thenReturn(keyEntry);

    SigningKeyDTO result =
        service.authorizeScheduleCommand(
            scheduleKey(),
            new ScheduleCommandEnvelope(
                oneTimeSchedule(startCommand("proc", -1)), true, keyId, null));

    assertThat(result).isEqualTo(keyEntry);
  }

  @Test
  void scheduleCommand_missingSignatureRejected() {
    globalConfigStore.update(signingConfig());

    assertThatThrownBy(
            () ->
                service.authorizeScheduleCommand(
                    scheduleKey(),
                    new ScheduleCommandEnvelope(
                        oneTimeSchedule(startCommand("proc", -1)), false, null, null)))
        .isInstanceOf(AuthorizationTokenException.class)
        .hasMessageContaining("missing or unverified signature");
  }

  @Test
  void scheduleCommand_clientKeyRejected() {
    globalConfigStore.update(signingConfig());

    String keyId = "client-schedule-key";
    SigningKeyDTO keyEntry =
        SigningKeyDTO.builder()
            .keyId(keyId)
            .publicKeyBase64("dummy")
            .algorithm("Ed25519")
            .status(KeyStatus.ACTIVE)
            .role(KeyRole.CLIENT)
            .build();
    when(signingKeysStore.get(keyId)).thenReturn(keyEntry);

    assertThatThrownBy(
            () ->
                service.authorizeScheduleCommand(
                    scheduleKey(),
                    new ScheduleCommandEnvelope(
                        oneTimeSchedule(startCommand("proc", -1)), true, keyId, null)))
        .isInstanceOf(AuthorizationTokenException.class)
        .hasMessageContaining("not trusted for role");
  }

  @Test
  void messageEventIngress_securityDisabled_returnsNull() {
    DefinitionMessageEventTriggerDTO messageEvent =
        new DefinitionMessageEventTriggerDTO("payment-received", VariablesDTO.empty());

    SigningKeyDTO result =
        service.authorizeMessageEventIngress(
            new RecordHeaders(),
            new MessageEventIngressEnvelope(new byte[0], messageEvent, false, null, null));

    assertThat(result).isNull();
  }

  @Test
  void messageEventIngress_signingEnabled_trustedClientKeyAccepted() {
    globalConfigStore.update(signingConfig());

    String keyId = "message-event-client-key";
    SigningKeyDTO keyEntry =
        SigningKeyDTO.builder()
            .keyId(keyId)
            .publicKeyBase64("dummy")
            .algorithm("Ed25519")
            .status(KeyStatus.ACTIVE)
            .role(KeyRole.CLIENT)
            .build();
    when(signingKeysStore.get(keyId)).thenReturn(keyEntry);

    CorrelationMessageEventTriggerDTO messageEvent =
        new CorrelationMessageEventTriggerDTO(
            "payment-received", "invoice-1", VariablesDTO.empty());

    SigningKeyDTO result =
        service.authorizeMessageEventIngress(
            headersWithSignature(keyId),
            new MessageEventIngressEnvelope(new byte[0], messageEvent, true, keyId, null));

    assertThat(result).isEqualTo(keyEntry);
  }

  @Test
  void messageEventIngress_anchoredPolicy_missingSignatureRejected() {
    when(config.isAnchored()).thenReturn(true);
    service =
        new EngineAuthorizationService(config, globalConfigStore, publicKeyProvider, kafkaStreams);
    DefinitionMessageEventTriggerDTO messageEvent =
        new DefinitionMessageEventTriggerDTO("payment-received", VariablesDTO.empty());

    assertThatThrownBy(
            () ->
                service.authorizeMessageEventIngress(
                    new RecordHeaders(),
                    new MessageEventIngressEnvelope(new byte[0], messageEvent, false, null, null)))
        .isInstanceOf(AuthorizationTokenException.class)
        .hasMessageContaining("tx-sig");
  }

  @Test
  void signalIngress_securityDisabled_returnsNull() {
    SigningKeyDTO result =
        service.authorizeSignalIngress(
            new RecordHeaders(),
            new SignalIngressEnvelope(
                new byte[0], new SignalDTO("order-placed"), false, null, null));

    assertThat(result).isNull();
  }

  @Test
  void signalIngress_signingEnabled_trustedClientKeyAccepted() {
    globalConfigStore.update(signingConfig());

    String keyId = "signal-client-key";
    SigningKeyDTO keyEntry =
        SigningKeyDTO.builder()
            .keyId(keyId)
            .publicKeyBase64("dummy")
            .algorithm("Ed25519")
            .status(KeyStatus.ACTIVE)
            .role(KeyRole.CLIENT)
            .build();
    when(signingKeysStore.get(keyId)).thenReturn(keyEntry);

    SigningKeyDTO result =
        service.authorizeSignalIngress(
            headersWithSignature(keyId),
            new SignalIngressEnvelope(
                new byte[0], new SignalDTO("order-placed"), true, keyId, null));

    assertThat(result).isEqualTo(keyEntry);
  }

  @Test
  void signalIngress_signatureErrorRejected() {
    globalConfigStore.update(signingConfig());

    assertThatThrownBy(
            () ->
                service.authorizeSignalIngress(
                    headersWithSignature("signal-client-key"),
                    new SignalIngressEnvelope(
                        new byte[0],
                        new SignalDTO("order-placed"),
                        false,
                        "signal-client-key",
                        "Malformed base64 signature for keyId=signal-client-key")))
        .isInstanceOf(AuthorizationTokenException.class)
        .hasMessageContaining("Malformed base64 signature");
  }

  // ── missing header ─────────────────────────────────────────────────────────

  @Test
  void missingHeader_withLegacyAuthFlagsOnly_returnsNull() {
    globalConfigStore.update(authorizationConfig());

    assertThat(service.authorize(new RecordHeaders(), envelope(startCommand("proc", -1)))).isNull();
  }

  @Test
  void startCommand_workerSignedEntryCommand_acceptedWhenSigningEnabled() {
    globalConfigStore.update(config(true));

    String keyId = "worker-test-001";
    SigningKeyDTO keyEntry =
        SigningKeyDTO.builder()
            .keyId(keyId)
            .publicKeyBase64("dummy")
            .status(KeyStatus.ACTIVE)
            .role(KeyRole.CLIENT)
            .build();
    when(signingKeysStore.get(keyId)).thenReturn(keyEntry);

    RecordHeaders headers = new RecordHeaders();
    headers.add("tx-sig", (keyId + ".AABB").getBytes(StandardCharsets.UTF_8));

    CommandTrustMetadataDTO result =
        service.authorize(
            headers,
            new ProcessInstanceTriggerEnvelope(new byte[0], startCommand("proc", -1), true, keyId));

    assertThat(result.getVerificationResult())
        .isEqualTo(CommandTrustVerificationResult.SIGNATURE_VERIFIED);
    assertThat(result.getAuthMethod()).isEqualTo(CommandAuthMethod.ED25519);
    assertThat(result.getTrusted()).isTrue();
    assertThat(result.getSignerKeyId()).isEqualTo(keyId);
    assertThat(result.getSignerOwner()).isEqualTo("worker-test-001");
  }

  @Test
  void startCommand_engineSignedEntryCommand_accepted() {
    globalConfigStore.update(config(true));

    String keyId = "engine-test-key-1";
    SigningKeyDTO keyEntry =
        SigningKeyDTO.builder()
            .keyId(keyId)
            .publicKeyBase64("dummy")
            .status(KeyStatus.ACTIVE)
            .role(KeyRole.ENGINE)
            .build();
    when(signingKeysStore.get(keyId)).thenReturn(keyEntry);

    RecordHeaders headers = new RecordHeaders();
    headers.add("tx-sig", (keyId + ".AABB").getBytes(StandardCharsets.UTF_8));

    CommandTrustMetadataDTO result =
        service.authorize(
            headers,
            new ProcessInstanceTriggerEnvelope(new byte[0], startCommand("proc", -1), true, keyId));
    assertThat(result.getVerificationResult())
        .isEqualTo(CommandTrustVerificationResult.ENGINE_SIGNED);
    assertThat(result.getAuthMethod()).isEqualTo(CommandAuthMethod.ED25519);
    assertThat(result.getTrusted()).isTrue();
    assertThat(result.getSignerKeyId()).isEqualTo(keyId);
    assertThat(result.getSignerOwner()).isEqualTo("engine-test-key-1");
  }

  @Test
  void startCommand_nullRoleSignedEntryCommand_acceptedAsClientSignature() {
    globalConfigStore.update(config(true));

    String keyId = "legacy-key-001";
    // No role set → defaults to null in builder → effectiveRole() returns CLIENT
    SigningKeyDTO nullRoleKey =
        new SigningKeyDTO(keyId, "dummy", "Ed25519", null, KeyStatus.ACTIVE, null, null);
    when(signingKeysStore.get(keyId)).thenReturn(nullRoleKey);

    RecordHeaders headers = new RecordHeaders();
    headers.add("tx-sig", (keyId + ".AABB").getBytes(StandardCharsets.UTF_8));

    CommandTrustMetadataDTO result =
        service.authorize(
            headers,
            new ProcessInstanceTriggerEnvelope(new byte[0], startCommand("proc", -1), true, keyId));

    assertThat(result.getVerificationResult())
        .isEqualTo(CommandTrustVerificationResult.SIGNATURE_VERIFIED);
    assertThat(result.getAuthMethod()).isEqualTo(CommandAuthMethod.ED25519);
    assertThat(result.getTrusted()).isTrue();
    assertThat(result.getSignerKeyId()).isEqualTo(keyId);
    assertThat(result.getSignerOwner()).isEqualTo("legacy-key-001");
  }

  @Test
  void abortTrigger_engineSignedEntryCommand_accepted() {
    globalConfigStore.update(config(true));

    String keyId = "engine-test-key-2";
    SigningKeyDTO keyEntry =
        SigningKeyDTO.builder()
            .keyId(keyId)
            .publicKeyBase64("dummy")
            .status(KeyStatus.ACTIVE)
            .role(KeyRole.ENGINE)
            .build();
    when(signingKeysStore.get(keyId)).thenReturn(keyEntry);

    RecordHeaders headers = new RecordHeaders();
    headers.add("tx-sig", (keyId + ".AABB").getBytes(StandardCharsets.UTF_8));

    AbortTriggerDTO cmd = new AbortTriggerDTO(java.util.UUID.randomUUID(), List.of());
    CommandTrustMetadataDTO result =
        service.authorize(
            headers, new ProcessInstanceTriggerEnvelope(new byte[0], cmd, true, keyId));
    assertThat(result.getVerificationResult())
        .isEqualTo(CommandTrustVerificationResult.ENGINE_SIGNED);
    assertThat(result.getTrusted()).isTrue();
  }

  @Test
  void startCommand_noHeadersWithAuthRequired_throwsMissingError() {
    globalConfigStore.update(config(true));

    assertThatThrownBy(
            () -> service.authorize(new RecordHeaders(), envelope(startCommand("proc", -1))))
        .isInstanceOf(AuthorizationTokenException.class)
        .hasMessageContaining("Entry command");
  }

  // ── claim mismatch ─────────────────────────────────────────────────────────

  @Test
  void wrongAction_forStart_throwsAuthorizationTokenException() {
    globalConfigStore.update(authorizationConfig());

    String jwt = buildJwt("CANCEL", "my-proc", -1, UUID.randomUUID().toString(), futureExpiry());
    assertThatThrownBy(
            () -> service.authorize(headersWithAuth(jwt), envelope(startCommand("my-proc", -1))))
        .isInstanceOf(AuthorizationTokenException.class)
        .hasMessageContaining("action");
  }

  @Test
  void wrongProcessDefinitionId_throwsAuthorizationTokenException() {
    globalConfigStore.update(authorizationConfig());

    String jwt = buildJwt("START", "proc-A", -1, UUID.randomUUID().toString(), futureExpiry());
    assertThatThrownBy(
            () -> service.authorize(headersWithAuth(jwt), envelope(startCommand("proc-B", -1))))
        .isInstanceOf(AuthorizationTokenException.class)
        .hasMessageContaining("processDefinitionId");
  }

  @Test
  void wrongVersion_throwsAuthorizationTokenException() {
    globalConfigStore.update(authorizationConfig());

    String jwt = buildJwt("START", "proc", 2, UUID.randomUUID().toString(), futureExpiry());
    assertThatThrownBy(
            () -> service.authorize(headersWithAuth(jwt), envelope(startCommand("proc", 3))))
        .isInstanceOf(AuthorizationTokenException.class)
        .hasMessageContaining("version");
  }

  // ── Ed25519 passthrough — non-entry (engine-internal continuations) ───────

  @Test
  void nonEntryTrigger_clientSignedExternalTaskResponse_authOnlyConfig_returnsNull() {
    globalConfigStore.update(externalTaskAuthorizationConfig(false));

    String keyId = "worker-test-001";
    when(signingKeysStore.get(keyId))
        .thenReturn(
            SigningKeyDTO.builder()
                .keyId(keyId)
                .publicKeyBase64("dummy")
                .status(KeyStatus.ACTIVE)
                .role(KeyRole.CLIENT)
                .build());

    RecordHeaders headers = new RecordHeaders();
    headers.add("tx-sig", (keyId + ".AABB").getBytes(StandardCharsets.UTF_8));

    assertThat(
            service.authorize(
                headers,
                new ProcessInstanceTriggerEnvelope(
                    new byte[0], externalTaskResponseTrigger(), true, keyId)))
        .isNull();
  }

  @Test
  void nonEntryTrigger_engineSignedContinuation_returnsSignerMetadata() {
    globalConfigStore.update(signingConfig());

    String keyId = "engine-test-key-1";
    when(signingKeysStore.get(keyId))
        .thenReturn(
            SigningKeyDTO.builder()
                .keyId(keyId)
                .publicKeyBase64("dummy")
                .status(KeyStatus.ACTIVE)
                .role(KeyRole.ENGINE)
                .build());

    RecordHeaders headers = new RecordHeaders();
    headers.add("tx-sig", (keyId + ".AABB").getBytes(StandardCharsets.UTF_8));

    CommandTrustMetadataDTO result =
        service.authorize(
            headers,
            new ProcessInstanceTriggerEnvelope(
                new byte[0], continueFlowElementTrigger(), true, keyId));
    assertThat(result)
        .isEqualTo(
            CommandTrustMetadataDTO.builder()
                .authMethod(CommandAuthMethod.ED25519)
                .verificationResult(CommandTrustVerificationResult.ENGINE_SIGNED)
                .trusted(true)
                .signerKeyId(keyId)
                .signerOwner("engine-test-key-1")
                .build());
  }

  @Test
  void nonEntryTrigger_clientSignedContinuation_rejectedForEngineOnlyMessageType() {
    globalConfigStore.update(signingConfig());

    String keyId = "worker-test-002";
    when(signingKeysStore.get(keyId))
        .thenReturn(
            SigningKeyDTO.builder()
                .keyId(keyId)
                .publicKeyBase64("dummy")
                .status(KeyStatus.ACTIVE)
                .role(KeyRole.CLIENT)
                .build());

    RecordHeaders headers = new RecordHeaders();
    headers.add("tx-sig", (keyId + ".AABB").getBytes(StandardCharsets.UTF_8));

    assertThatThrownBy(
            () ->
                service.authorize(
                    headers,
                    new ProcessInstanceTriggerEnvelope(
                        new byte[0], continueFlowElementTrigger(), true, keyId)))
        .isInstanceOf(AuthorizationTokenException.class)
        .hasMessageContaining("not trusted for required role");
  }

  @Test
  void nonEntryTrigger_withoutHeaders_authOnlyConfig_returnsNull() {
    globalConfigStore.update(config(false));

    assertThat(service.authorize(new RecordHeaders(), envelope(continueFlowElementTrigger())))
        .isNull();
  }

  @Test
  void nonEntryTrigger_withEmbeddedTrust_authOnlyConfig_returnsNull() {
    globalConfigStore.update(config(false));

    ContinueFlowElementTriggerDTO trigger = continueFlowElementTrigger();
    CommandTrustMetadataDTO embeddedMetadata =
        CommandTrustMetadataDTO.builder()
            .authMethod(CommandAuthMethod.JWT)
            .verificationResult(CommandTrustVerificationResult.JWT_AUTHORIZED)
            .trusted(true)
            .userId("service-account-1")
            .issuer(ISSUER)
            .build();
    trigger.setCurrentTrustMetadata(embeddedMetadata);
    trigger.setOriginTrustMetadata(embeddedMetadata);

    assertThat(service.authorize(new RecordHeaders(), envelope(trigger))).isNull();
  }

  @Test
  void nonEntryTrigger_signatureError_throwsAuthorizationTokenException() {
    globalConfigStore.update(signingConfig());

    String keyId = "worker-test-001";
    when(signingKeysStore.get(keyId))
        .thenReturn(
            SigningKeyDTO.builder()
                .keyId(keyId)
                .publicKeyBase64("dummy")
                .status(KeyStatus.ACTIVE)
                .build());

    RecordHeaders headers = new RecordHeaders();
    headers.add("tx-sig", (keyId + ".AABB").getBytes(StandardCharsets.UTF_8));

    assertThatThrownBy(
            () ->
                service.authorize(
                    headers,
                    new ProcessInstanceTriggerEnvelope(
                        new byte[0],
                        continueFlowElementTrigger(),
                        false,
                        keyId,
                        "Malformed base64 signature for keyId=" + keyId)))
        .isInstanceOf(AuthorizationTokenException.class)
        .hasMessageContaining("Malformed base64 signature");
  }

  // ── SetVariableTriggerDTO as external entry command ────────────────────────

  @Test
  void setVariableCommand_noHeaders_authOnlyConfig_returnsNull() {
    globalConfigStore.update(authorizationConfig());

    assertThat(service.authorize(new RecordHeaders(), envelope(setVariableTrigger()))).isNull();
  }

  @Test
  void setVariableCommand_noHeaders_bothGatesRequired_throws() {
    globalConfigStore.update(config(true));

    assertThatThrownBy(() -> service.authorize(new RecordHeaders(), envelope(setVariableTrigger())))
        .isInstanceOf(AuthorizationTokenException.class)
        .hasMessageContaining("Entry command");
  }

  @Test
  void setVariableCommand_engineSigned_accepted() {
    globalConfigStore.update(config(true));

    String keyId = "engine-test-key-3";
    when(signingKeysStore.get(keyId))
        .thenReturn(
            SigningKeyDTO.builder()
                .keyId(keyId)
                .publicKeyBase64("dummy")
                .status(KeyStatus.ACTIVE)
                .role(KeyRole.ENGINE)
                .build());

    RecordHeaders headers = new RecordHeaders();
    headers.add("tx-sig", (keyId + ".AABB").getBytes(StandardCharsets.UTF_8));

    CommandTrustMetadataDTO result =
        service.authorize(
            headers,
            new ProcessInstanceTriggerEnvelope(new byte[0], setVariableTrigger(), true, keyId));
    assertThat(result.getVerificationResult())
        .isEqualTo(CommandTrustVerificationResult.ENGINE_SIGNED);
    assertThat(result.getAuthMethod()).isEqualTo(CommandAuthMethod.ED25519);
    assertThat(result.getTrusted()).isTrue();
    assertThat(result.getSignerKeyId()).isEqualTo(keyId);
    assertThat(result.getSignerOwner()).isEqualTo("engine-test-key-3");
  }

  @Test
  void setVariableCommand_wrongAction_throwsAuthorizationTokenException() {
    globalConfigStore.update(authorizationConfig());

    String jwt = buildJwt("START", null, -1, UUID.randomUUID().toString(), futureExpiry());
    assertThatThrownBy(
            () -> service.authorize(headersWithAuth(jwt), envelope(setVariableTrigger())))
        .isInstanceOf(AuthorizationTokenException.class)
        .hasMessageContaining("action");
  }

  @Test
  void userTaskCompletion_wrongAction_throwsAuthorizationTokenException() {
    globalConfigStore.update(userTaskAuthorizationConfig(false));

    String jwt = buildJwt("START", null, -1, UUID.randomUUID().toString(), futureExpiry());

    assertThatThrownBy(
            () -> service.authorize(headersWithAuth(jwt), envelope(userTaskResponseTrigger())))
        .isInstanceOf(AuthorizationTokenException.class)
        .hasMessageContaining("USER_TASK_COMPLETE");
  }

  @Test
  void userTaskCompletion_missingJwt_authOnlyConfig_returnsNull() {
    globalConfigStore.update(userTaskAuthorizationConfig(false));

    assertThat(service.authorize(new RecordHeaders(), envelope(userTaskResponseTrigger())))
        .isNull();
  }

  @Test
  void userTaskCompletion_missingSignature_whenSigningEnabled_throws() {
    globalConfigStore.update(userTaskAuthorizationConfig(true));

    String jwt =
        buildJwt("USER_TASK_COMPLETE", null, -1, UUID.randomUUID().toString(), futureExpiry());

    assertThatThrownBy(
            () -> service.authorize(headersWithAuth(jwt), envelope(userTaskResponseTrigger())))
        .isInstanceOf(AuthorizationTokenException.class)
        .hasMessageContaining("tx-sig");
  }

  @Test
  void externalTaskCompletion_clientSignatureWithoutJwt_authOnlyConfig_isIgnored() {
    globalConfigStore.update(externalTaskAuthorizationConfig(false));

    String keyId = "worker-task-complete-key";
    when(signingKeysStore.get(keyId))
        .thenReturn(
            SigningKeyDTO.builder()
                .keyId(keyId)
                .publicKeyBase64("dummy")
                .status(KeyStatus.ACTIVE)
                .role(KeyRole.CLIENT)
                .build());

    assertThat(
            service.authorize(
                headersWithSignature(keyId),
                new ProcessInstanceTriggerEnvelope(
                    new byte[0], externalTaskResponseTrigger(), true, keyId)))
        .isNull();
  }

  @Test
  void commandAuthorizationFlagDoesNotApplyToUserTaskCompletion() {
    globalConfigStore.update(commandAuthorizationOnlyConfig());

    assertThat(service.authorize(new RecordHeaders(), envelope(userTaskResponseTrigger())))
        .isNull();
  }

  @Test
  void commandAuthorizationFlagDoesNotApplyToExternalTaskCompletion() {
    globalConfigStore.update(commandAuthorizationOnlyConfig());

    assertThat(service.authorize(new RecordHeaders(), envelope(externalTaskResponseTrigger())))
        .isNull();
  }

  @Test
  void authoritativeAnchoredPolicy_startCommandRequiresSignatureNotJwt() {
    when(config.isAnchored()).thenReturn(true);
    service =
        new EngineAuthorizationService(config, globalConfigStore, publicKeyProvider, kafkaStreams);

    assertThatThrownBy(
            () -> service.authorize(new RecordHeaders(), envelope(startCommand("proc", -1))))
        .isInstanceOf(AuthorizationTokenException.class)
        .hasMessageContaining("Entry command")
        .hasMessageContaining("tx-sig");
  }

  @Test
  void authoritativeAnchoredPolicy_clientSignedStartCommandAcceptedWithoutJwt() {
    when(config.isAnchored()).thenReturn(true);
    service =
        new EngineAuthorizationService(config, globalConfigStore, publicKeyProvider, kafkaStreams);

    String keyId = "anchored-client-key";
    when(signingKeysStore.get(keyId))
        .thenReturn(
            SigningKeyDTO.builder()
                .keyId(keyId)
                .publicKeyBase64("dummy")
                .status(KeyStatus.ACTIVE)
                .role(KeyRole.CLIENT)
                .build());

    CommandTrustMetadataDTO result =
        service.authorize(
            headersWithSignature(keyId),
            new ProcessInstanceTriggerEnvelope(new byte[0], startCommand("proc", -1), true, keyId));

    assertThat(result.getAuthMethod()).isEqualTo(CommandAuthMethod.ED25519);
    assertThat(result.getVerificationResult())
        .isEqualTo(CommandTrustVerificationResult.SIGNATURE_VERIFIED);
    assertThat(result.getSignerKeyId()).isEqualTo(keyId);
  }

  @Test
  void openAuthoritativePolicy_doesNotEnableAuthorization() {
    // OPEN mode (default) — no signing/authorization required
    assertThat(service.authorize(new RecordHeaders(), envelope(startCommand("proc", -1)))).isNull();
  }

  @Test
  void authoritativePolicy_doesNotEnableTaskCompletionJwtRequirement() {
    when(config.isAnchored()).thenReturn(true);
    service =
        new EngineAuthorizationService(config, globalConfigStore, publicKeyProvider, kafkaStreams);

    String keyId = "anchored-task-key";
    when(signingKeysStore.get(keyId))
        .thenReturn(
            SigningKeyDTO.builder()
                .keyId(keyId)
                .publicKeyBase64("dummy")
                .status(KeyStatus.ACTIVE)
                .role(KeyRole.CLIENT)
                .build());

    CommandTrustMetadataDTO userTaskResult =
        service.authorize(
            headersWithSignature(keyId),
            new ProcessInstanceTriggerEnvelope(
                new byte[0], userTaskResponseTrigger(), true, keyId));
    CommandTrustMetadataDTO externalTaskResult =
        service.authorize(
            headersWithSignature(keyId),
            new ProcessInstanceTriggerEnvelope(
                new byte[0], externalTaskResponseTrigger(), true, keyId));

    assertThat(userTaskResult.getAuthMethod()).isEqualTo(CommandAuthMethod.ED25519);
    assertThat(userTaskResult.getVerificationResult())
        .isEqualTo(CommandTrustVerificationResult.SIGNATURE_VERIFIED);
    assertThat(externalTaskResult.getAuthMethod()).isEqualTo(CommandAuthMethod.ED25519);
    assertThat(externalTaskResult.getVerificationResult())
        .isEqualTo(CommandTrustVerificationResult.SIGNATURE_VERIFIED);
  }

  @Test
  void authoritativePolicy_clientCommandSigningRejectsUnsignedStartCommand() {
    when(config.isAnchored()).thenReturn(true);
    service =
        new EngineAuthorizationService(config, globalConfigStore, publicKeyProvider, kafkaStreams);

    assertThatThrownBy(
            () -> service.authorize(new RecordHeaders(), envelope(startCommand("proc", -1))))
        .isInstanceOf(AuthorizationTokenException.class)
        .hasMessageContaining("Entry command");
  }

  @Test
  void authoritativePolicy_workerResponseSigningRejectsUnsignedUserTaskCompletion() {
    when(config.isAnchored()).thenReturn(true);
    service =
        new EngineAuthorizationService(config, globalConfigStore, publicKeyProvider, kafkaStreams);

    assertThatThrownBy(
            () -> service.authorize(new RecordHeaders(), envelope(userTaskResponseTrigger())))
        .isInstanceOf(AuthorizationTokenException.class)
        .hasMessageContaining("tx-sig");
  }

  @Test
  void authoritativePolicy_engineOutboundSigningRejectsUnsignedScheduleCommand() {
    when(config.isAnchored()).thenReturn(true);
    service =
        new EngineAuthorizationService(config, globalConfigStore, publicKeyProvider, kafkaStreams);

    assertThatThrownBy(
            () ->
                service.authorizeScheduleCommand(
                    scheduleKey(),
                    new ScheduleCommandEnvelope(
                        oneTimeSchedule(startCommand("proc", -1)), false, null, null)))
        .isInstanceOf(AuthorizationTokenException.class)
        .hasMessageContaining("missing or unverified signature");
  }

  private GlobalConfigurationDTO authorizationConfig() {
    return config(true, true, true, false, ReplayProtectionMode.COMPAT);
  }

  /** Config with only {@code signingEnabled=true} — used for topic-meta and schedule-commands. */
  private GlobalConfigurationDTO signingConfig() {
    return config(false, false, false, true, ReplayProtectionMode.COMPAT);
  }

  private GlobalConfigurationDTO config(boolean signingEnabled) {
    return config(true, true, true, signingEnabled, ReplayProtectionMode.COMPAT);
  }

  private GlobalConfigurationDTO config(
      boolean engineRequiresAuthorization,
      boolean engineRequiresExternalTaskAuthorization,
      boolean engineRequiresUserTaskAuthorization,
      boolean signingEnabled,
      ReplayProtectionMode replayProtectionMode) {
    return GlobalConfigurationDTO.builder()
        .engineRequiresAuthorization(engineRequiresAuthorization)
        .engineRequiresExternalTaskAuthorization(engineRequiresExternalTaskAuthorization)
        .engineRequiresUserTaskAuthorization(engineRequiresUserTaskAuthorization)
        .signingEnabled(signingEnabled)
        .replayProtectionMode(replayProtectionMode)
        .build();
  }

  private GlobalConfigurationDTO commandAuthorizationOnlyConfig() {
    return config(true, false, false, false, ReplayProtectionMode.COMPAT);
  }

  private GlobalConfigurationDTO userTaskAuthorizationConfig(boolean signingEnabled) {
    return config(false, false, true, signingEnabled, ReplayProtectionMode.COMPAT);
  }

  private GlobalConfigurationDTO externalTaskAuthorizationConfig(boolean signingEnabled) {
    return config(false, true, false, signingEnabled, ReplayProtectionMode.COMPAT);
  }

  // ── helpers ────────────────────────────────────────────────────────────────

  private String buildJwt(
      String action, String processDefinitionId, int version, String auditId, Date expiry) {
    return buildJwt(action, processDefinitionId, version, auditId, "user-1", expiry);
  }

  private String buildJwt(
      String action,
      String processDefinitionId,
      int version,
      String auditId,
      String subject,
      Date expiry) {
    var builder =
        Jwts.builder()
            .header()
            .keyId(PLATFORM_KID)
            .and()
            .subject(subject)
            .issuer(ISSUER)
            .claim("action", action)
            .claim("version", version)
            .claim("namespaceId", UUID.randomUUID().toString())
            .claim("auditId", auditId)
            .expiration(expiry)
            .signWith(rsaKeyPair.getPrivate());
    if (processDefinitionId != null) {
      builder.claim("processDefinitionId", processDefinitionId);
    }
    return builder.compact();
  }

  private Headers headersWithAuth(String jwt) {
    RecordHeaders headers = new RecordHeaders();
    headers.add("tx-auth", jwt.getBytes(StandardCharsets.UTF_8));
    return headers;
  }

  private Headers headersWithSignature(String keyId) {
    RecordHeaders headers = new RecordHeaders();
    headers.add("tx-sig", (keyId + ".AABB").getBytes(StandardCharsets.UTF_8));
    return headers;
  }

  private DefinitionScheduleKeyDTO scheduleKey() {
    return new DefinitionScheduleKeyDTO(
        new ProcessDefinitionKey("proc", 1), "timer-start", TimeBucket.MINUTE);
  }

  private OneTimeScheduleDTO oneTimeSchedule(StartCommandDTO command) {
    return new OneTimeScheduleDTO(
        command, Instant.now().toEpochMilli(), Instant.now().plusSeconds(60).toEpochMilli());
  }

  private ProcessInstanceTriggerEnvelope envelope(StartCommandDTO trigger) {
    return new ProcessInstanceTriggerEnvelope(new byte[0], trigger, false, null);
  }

  private ProcessInstanceTriggerEnvelope envelope(AbortTriggerDTO trigger) {
    return new ProcessInstanceTriggerEnvelope(new byte[0], trigger, false, null);
  }

  private ProcessInstanceTriggerEnvelope envelope(ContinueFlowElementTriggerDTO trigger) {
    return new ProcessInstanceTriggerEnvelope(new byte[0], trigger, false, null);
  }

  private ProcessInstanceTriggerEnvelope envelope(SetVariableTriggerDTO trigger) {
    return new ProcessInstanceTriggerEnvelope(new byte[0], trigger, false, null);
  }

  private StartCommandDTO startCommand(String processDefinitionId, int version) {
    return new StartCommandDTO(
        UUID.randomUUID(),
        null,
        null,
        processDefinitionId != null ? new ProcessDefinitionKey(processDefinitionId, version) : null,
        VariablesDTO.empty());
  }

  private ContinueFlowElementTriggerDTO continueFlowElementTrigger() {
    return new ContinueFlowElementTriggerDTO(
        UUID.randomUUID(), List.of(1L), "flow-1", VariablesDTO.empty());
  }

  private ExternalTaskResponseTriggerDTO externalTaskResponseTrigger() {
    return new ExternalTaskResponseTriggerDTO(
        UUID.randomUUID(),
        List.of(1L),
        new ExternalTaskResponseResultDTO(ExternalTaskResponseType.SUCCESS, true, null, null, 0L),
        VariablesDTO.empty());
  }

  private UserTaskResponseTriggerDTO userTaskResponseTrigger() {
    return new UserTaskResponseTriggerDTO(
        UUID.randomUUID(),
        List.of(1L),
        new UserTaskResponseResultDTO(UserTaskResponseType.COMPLETED, null, null),
        VariablesDTO.empty());
  }

  private ProcessInstanceTriggerEnvelope envelope(ExternalTaskResponseTriggerDTO trigger) {
    return new ProcessInstanceTriggerEnvelope(new byte[0], trigger, false, null);
  }

  private ProcessInstanceTriggerEnvelope envelope(UserTaskResponseTriggerDTO trigger) {
    return new ProcessInstanceTriggerEnvelope(new byte[0], trigger, false, null);
  }

  private SetVariableTriggerDTO setVariableTrigger() {
    return new SetVariableTriggerDTO(UUID.randomUUID(), List.of(1L), VariablesDTO.empty());
  }

  private Date futureExpiry() {
    return Date.from(Instant.now().plusSeconds(300));
  }
}
