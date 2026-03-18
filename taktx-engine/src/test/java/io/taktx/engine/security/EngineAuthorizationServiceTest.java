/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks All rights reserved.
 * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 * Free use is permitted with up to 3 Kafka partitions per topic. See LICENSE file for details.
 * For commercial use or more partitions and features, contact [https://www.taktx.io/contact].
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
import io.taktx.dto.GlobalConfigurationDTO;
import io.taktx.dto.ProcessDefinitionKey;
import io.taktx.dto.SigningKeyDTO;
import io.taktx.dto.SigningKeyDTO.KeyStatus;
import io.taktx.dto.StartCommandDTO;
import io.taktx.dto.VariablesDTO;
import io.taktx.engine.config.GlobalConfigStore;
import io.taktx.engine.config.TaktConfiguration;
import io.taktx.engine.pi.ProcessInstanceTriggerEnvelope;
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
  private NonceStore nonceStore;
  private KafkaStreams kafkaStreams;
  private ReadOnlyKeyValueStore<String, SigningKeyDTO> signingKeysStore;
  private EngineAuthorizationService service;

  private java.security.KeyPair rsaKeyPair;

  @BeforeEach
  void setUp() throws Exception {
    rsaKeyPair = KeyPairGenerator.getInstance("RSA").generateKeyPair();

    config = mock(TaktConfiguration.class);
    globalConfigStore = new GlobalConfigStore();
    publicKeyProvider = mock(PublicKeyProvider.class);
    nonceStore = new NonceStore();
    kafkaStreams = mock(KafkaStreams.class);
    signingKeysStore = mock(ReadOnlyKeyValueStore.class);

    when(publicKeyProvider.getKey(PLATFORM_KID)).thenReturn(rsaKeyPair.getPublic());
    when(kafkaStreams.store(org.mockito.ArgumentMatchers.any())).thenReturn(signingKeysStore);
    when(config.getPrefixed(org.mockito.ArgumentMatchers.any()))
        .thenReturn("default.taktx-signing-keys");

    service =
        new EngineAuthorizationService(
            config, globalConfigStore, publicKeyProvider, nonceStore, kafkaStreams);
  }

  // ── authorization disabled ─────────────────────────────────────────────────

  @Test
  void disabled_returnsNull_forAnyCommand() {
    assertThat(service.authorize(new RecordHeaders(), envelope(startCommand("proc", -1)))).isNull();
  }

  // ── valid JWT token ────────────────────────────────────────────────────────

  @Test
  void validToken_start_returnsJwtMetadata() {
    globalConfigStore.update(authorizationConfig(true));

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
    globalConfigStore.update(authorizationConfig(true));

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

  // ── missing header ─────────────────────────────────────────────────────────

  @Test
  void missingHeader_throwsAuthorizationTokenException() {
    globalConfigStore.update(authorizationConfig(true));
    assertThatThrownBy(
            () -> service.authorize(new RecordHeaders(), envelope(startCommand("proc", -1))))
        .isInstanceOf(AuthorizationTokenException.class)
        .hasMessageContaining("Missing");
  }

  // ── claim mismatch ─────────────────────────────────────────────────────────

  @Test
  void wrongAction_forStart_throwsAuthorizationTokenException() {
    globalConfigStore.update(authorizationConfig(true));

    String jwt = buildJwt("CANCEL", "my-proc", -1, UUID.randomUUID().toString(), futureExpiry());
    assertThatThrownBy(
            () -> service.authorize(headersWithAuth(jwt), envelope(startCommand("my-proc", -1))))
        .isInstanceOf(AuthorizationTokenException.class)
        .hasMessageContaining("action");
  }

  @Test
  void wrongProcessDefinitionId_throwsAuthorizationTokenException() {
    globalConfigStore.update(authorizationConfig(true));

    String jwt = buildJwt("START", "proc-A", -1, UUID.randomUUID().toString(), futureExpiry());
    assertThatThrownBy(
            () -> service.authorize(headersWithAuth(jwt), envelope(startCommand("proc-B", -1))))
        .isInstanceOf(AuthorizationTokenException.class)
        .hasMessageContaining("processDefinitionId");
  }

  @Test
  void wrongVersion_throwsAuthorizationTokenException() {
    globalConfigStore.update(authorizationConfig(true));

    String jwt = buildJwt("START", "proc", 2, UUID.randomUUID().toString(), futureExpiry());
    assertThatThrownBy(
            () -> service.authorize(headersWithAuth(jwt), envelope(startCommand("proc", 3))))
        .isInstanceOf(AuthorizationTokenException.class)
        .hasMessageContaining("version");
  }

  // ── nonce / replay ─────────────────────────────────────────────────────────

  @Test
  void replayedAuditId_throwsAuthorizationTokenException() {
    globalConfigStore.update(authorizationConfig(true));

    String auditId = UUID.randomUUID().toString();
    String jwt = buildJwt("START", null, -1, auditId, futureExpiry());
    Headers headers = headersWithAuth(jwt);

    service.authorize(headers, envelope(startCommand(null, -1)));
    assertThatThrownBy(() -> service.authorize(headers, envelope(startCommand(null, -1))))
        .isInstanceOf(AuthorizationTokenException.class)
        .hasMessageContaining("Replayed");
  }

  // ── Ed25519 passthrough (already verified in deserializer) ─────────────────

  @Test
  void ed25519Header_present_returnsSignerMetadata() {
    globalConfigStore.update(authorizationConfig(true));

    String keyId = "worker-test-001";
    SigningKeyDTO keyEntry =
        SigningKeyDTO.builder()
            .keyId(keyId)
            .publicKeyBase64("dummy")
            .status(KeyStatus.ACTIVE)
            .owner("worker-billing")
            .build();
    when(signingKeysStore.get(keyId)).thenReturn(keyEntry);

    RecordHeaders headers = new RecordHeaders();
    headers.add("X-TaktX-Signature", (keyId + ".AABB").getBytes(StandardCharsets.UTF_8));

    // Ed25519 is already verified in the deserializer — authorize just passes through
    CommandTrustMetadataDTO result =
        service.authorize(
            headers, new ProcessInstanceTriggerEnvelope(startCommand("proc", -1), true, keyId));
    assertThat(result)
        .isEqualTo(
            CommandTrustMetadataDTO.builder()
                .authMethod(CommandAuthMethod.ED25519)
                .verificationResult(CommandTrustVerificationResult.SIGNATURE_VERIFIED)
                .trusted(true)
                .signerKeyId(keyId)
                .signerOwner("worker-billing")
                .build());
  }

  @Test
  void ed25519Header_knownEngineKeyInStore_preservesEmbeddedMetadata() {
    globalConfigStore.update(authorizationConfig(true));

    String keyId = "engine-test-key-1";
    SigningKeyDTO keyEntry =
        SigningKeyDTO.builder()
            .keyId(keyId)
            .publicKeyBase64("dummy")
            .status(KeyStatus.ACTIVE)
            .owner("engine")
            .build();
    when(signingKeysStore.get(keyId)).thenReturn(keyEntry);

    StartCommandDTO trigger = startCommand("proc", -1);
    CommandTrustMetadataDTO embeddedMetadata =
        CommandTrustMetadataDTO.builder()
            .authMethod(CommandAuthMethod.JWT)
            .verificationResult(CommandTrustVerificationResult.JWT_AUTHORIZED)
            .trusted(true)
            .userId("user-1")
            .issuer(ISSUER)
            .build();
    trigger.setCommandTrustMetadata(embeddedMetadata);

    RecordHeaders headers = new RecordHeaders();
    headers.add("X-TaktX-Signature", (keyId + ".AABB").getBytes(StandardCharsets.UTF_8));

    CommandTrustMetadataDTO result =
        service.authorize(headers, new ProcessInstanceTriggerEnvelope(trigger, true, keyId));
    assertThat(result).isEqualTo(embeddedMetadata);
  }

  @Test
  void ed25519Envelope_signatureError_throwsAuthorizationTokenException() {
    globalConfigStore.update(authorizationConfig(true));

    String keyId = "worker-test-001";
    SigningKeyDTO keyEntry =
        SigningKeyDTO.builder()
            .keyId(keyId)
            .publicKeyBase64("dummy")
            .status(KeyStatus.ACTIVE)
            .owner("worker-billing")
            .build();
    when(signingKeysStore.get(keyId)).thenReturn(keyEntry);

    RecordHeaders headers = new RecordHeaders();
    headers.add("X-TaktX-Signature", (keyId + ".AABB").getBytes(StandardCharsets.UTF_8));

    assertThatThrownBy(
            () ->
                service.authorize(
                    headers,
                    new ProcessInstanceTriggerEnvelope(
                        startCommand("proc", -1),
                        false,
                        keyId,
                        "Malformed base64 signature for keyId=worker-test-001")))
        .isInstanceOf(AuthorizationTokenException.class)
        .hasMessageContaining("Malformed base64 signature for keyId=worker-test-001");
  }

  private GlobalConfigurationDTO authorizationConfig(boolean engineRequiresAuthorization) {
    return GlobalConfigurationDTO.builder()
        .engineRequiresAuthorization(engineRequiresAuthorization)
        .build();
  }

  // ── helpers ────────────────────────────────────────────────────────────────

  private String buildJwt(
      String action, String processDefinitionId, int version, String auditId, Date expiry) {
    var builder =
        Jwts.builder()
            .header()
            .keyId(PLATFORM_KID)
            .and()
            .subject("user-1")
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
    headers.add("X-TaktX-Authorization", jwt.getBytes(StandardCharsets.UTF_8));
    return headers;
  }

  private ProcessInstanceTriggerEnvelope envelope(StartCommandDTO trigger) {
    return new ProcessInstanceTriggerEnvelope(trigger, false, null);
  }

  private ProcessInstanceTriggerEnvelope envelope(AbortTriggerDTO trigger) {
    return new ProcessInstanceTriggerEnvelope(trigger, false, null);
  }

  private StartCommandDTO startCommand(String processDefinitionId, int version) {
    return new StartCommandDTO(
        UUID.randomUUID(),
        null,
        null,
        processDefinitionId != null ? new ProcessDefinitionKey(processDefinitionId, version) : null,
        VariablesDTO.empty());
  }

  private Date futureExpiry() {
    return Date.from(Instant.now().plusSeconds(300));
  }
}
