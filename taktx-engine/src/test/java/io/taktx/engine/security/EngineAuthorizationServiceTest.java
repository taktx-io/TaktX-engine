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
import io.taktx.dto.GlobalConfigurationDTO;
import io.taktx.dto.ProcessDefinitionKey;
import io.taktx.dto.SigningKeyDTO;
import io.taktx.dto.SigningKeyDTO.KeyStatus;
import io.taktx.dto.StartCommandDTO;
import io.taktx.dto.VariablesDTO;
import io.taktx.engine.config.GlobalConfigStore;
import io.taktx.engine.config.TaktConfiguration;
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
    assertThat(service.authorize(new RecordHeaders(), startCommand("proc", -1))).isNull();
  }

  // ── valid JWT token ────────────────────────────────────────────────────────

  @Test
  void validToken_start_returnsAuditId() {
    globalConfigStore.update(authorizationConfig(true));

    String auditId = UUID.randomUUID().toString();
    String jwt = buildJwt("START", "my-proc", -1, auditId, futureExpiry());

    String result = service.authorize(headersWithAuth(jwt), startCommand("my-proc", -1));
    assertThat(result).isEqualTo(auditId);
  }

  @Test
  void validToken_cancel_returnsAuditId() {
    globalConfigStore.update(authorizationConfig(true));

    String auditId = UUID.randomUUID().toString();
    String jwt = buildJwt("CANCEL", null, -1, auditId, futureExpiry());
    AbortTriggerDTO cmd = new AbortTriggerDTO(UUID.randomUUID(), List.of());

    String result = service.authorize(headersWithAuth(jwt), cmd);
    assertThat(result).isEqualTo(auditId);
  }

  // ── missing header ─────────────────────────────────────────────────────────

  @Test
  void missingHeader_throwsAuthorizationTokenException() {
    globalConfigStore.update(authorizationConfig(true));
    assertThatThrownBy(() -> service.authorize(new RecordHeaders(), startCommand("proc", -1)))
        .isInstanceOf(AuthorizationTokenException.class)
        .hasMessageContaining("Missing");
  }

  // ── claim mismatch ─────────────────────────────────────────────────────────

  @Test
  void wrongAction_forStart_throwsAuthorizationTokenException() {
    globalConfigStore.update(authorizationConfig(true));

    String jwt = buildJwt("CANCEL", "my-proc", -1, UUID.randomUUID().toString(), futureExpiry());
    assertThatThrownBy(() -> service.authorize(headersWithAuth(jwt), startCommand("my-proc", -1)))
        .isInstanceOf(AuthorizationTokenException.class)
        .hasMessageContaining("action");
  }

  @Test
  void wrongProcessDefinitionId_throwsAuthorizationTokenException() {
    globalConfigStore.update(authorizationConfig(true));

    String jwt = buildJwt("START", "proc-A", -1, UUID.randomUUID().toString(), futureExpiry());
    assertThatThrownBy(() -> service.authorize(headersWithAuth(jwt), startCommand("proc-B", -1)))
        .isInstanceOf(AuthorizationTokenException.class)
        .hasMessageContaining("processDefinitionId");
  }

  @Test
  void wrongVersion_throwsAuthorizationTokenException() {
    globalConfigStore.update(authorizationConfig(true));

    String jwt = buildJwt("START", "proc", 2, UUID.randomUUID().toString(), futureExpiry());
    assertThatThrownBy(() -> service.authorize(headersWithAuth(jwt), startCommand("proc", 3)))
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

    service.authorize(headers, startCommand(null, -1));
    assertThatThrownBy(() -> service.authorize(headers, startCommand(null, -1)))
        .isInstanceOf(AuthorizationTokenException.class)
        .hasMessageContaining("Replayed");
  }

  // ── Ed25519 passthrough (already verified in deserializer) ─────────────────

  @Test
  void ed25519Header_present_returnsNull_auditId() {
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
    String result = service.authorize(headers, startCommand("proc", -1));
    assertThat(result).isNull();
  }

  @Test
  void ed25519Header_knownEngineKeyInStore_returnsNull_auditId() {
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

    RecordHeaders headers = new RecordHeaders();
    headers.add("X-TaktX-Signature", (keyId + ".AABB").getBytes(StandardCharsets.UTF_8));

    String result = service.authorize(headers, startCommand("proc", -1));
    assertThat(result).isNull();
  }

  private GlobalConfigurationDTO authorizationConfig(boolean authorizationEnabled) {
    return GlobalConfigurationDTO.builder().authorizationEnabled(authorizationEnabled).build();
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
