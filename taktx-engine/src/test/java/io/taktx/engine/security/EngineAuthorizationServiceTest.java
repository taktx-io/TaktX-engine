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
import io.taktx.dto.ProcessDefinitionKey;
import io.taktx.dto.StartCommandDTO;
import io.taktx.dto.VariablesDTO;
import io.taktx.engine.config.TaktConfiguration;
import io.taktx.security.AuthorizationTokenException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import org.apache.kafka.common.header.Headers;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class EngineAuthorizationServiceTest {

  private static final String ISSUER = "taktx-platform-service";

  private TaktConfiguration config;
  private PublicKeyProvider publicKeyProvider;
  private NonceStore nonceStore;
  private EngineAuthorizationService service;

  private KeyPair rsaKeyPair;

  @BeforeEach
  void setUp() throws Exception {
    rsaKeyPair = KeyPairGenerator.getInstance("RSA").generateKeyPair();

    config = mock(TaktConfiguration.class);
    publicKeyProvider = mock(PublicKeyProvider.class);
    nonceStore = new NonceStore();

    when(publicKeyProvider.isReady()).thenReturn(true);
    when(publicKeyProvider.getPlatformKey()).thenReturn(rsaKeyPair.getPublic());

    service = new EngineAuthorizationService(config, publicKeyProvider, nonceStore);
  }

  // ── authorization disabled ─────────────────────────────────────────────────

  @Test
  void disabled_returnsNull_forAnyCommand() {
    when(config.isAuthorizationEnabled()).thenReturn(false);
    StartCommandDTO cmd = startCommand("proc", -1);
    String auditId = service.authorize(new RecordHeaders(), cmd);
    assertThat(auditId).isNull();
  }

  // ── valid token ────────────────────────────────────────────────────────────

  @Test
  void validToken_start_returnsAuditId() {
    when(config.isAuthorizationEnabled()).thenReturn(true);
    when(config.isNonceCheckEnabled()).thenReturn(false);

    String auditId = UUID.randomUUID().toString();
    String jwt = buildJwt("START", "my-proc", -1, auditId, futureExpiry());
    Headers headers = headersWithAuth(jwt);

    String result = service.authorize(headers, startCommand("my-proc", -1));
    assertThat(result).isEqualTo(auditId);
  }

  @Test
  void validToken_cancel_returnsAuditId() {
    when(config.isAuthorizationEnabled()).thenReturn(true);
    when(config.isNonceCheckEnabled()).thenReturn(false);

    String auditId = UUID.randomUUID().toString();
    String jwt = buildJwt("CANCEL", null, -1, auditId, futureExpiry());
    Headers headers = headersWithAuth(jwt);

    AbortTriggerDTO cmd = new AbortTriggerDTO(UUID.randomUUID(), List.of());
    String result = service.authorize(headers, cmd);
    assertThat(result).isEqualTo(auditId);
  }

  // ── missing / malformed header ─────────────────────────────────────────────

  @Test
  void missingHeader_throwsAuthorizationTokenException() {
    when(config.isAuthorizationEnabled()).thenReturn(true);
    assertThatThrownBy(() -> service.authorize(new RecordHeaders(), startCommand("proc", -1)))
        .isInstanceOf(AuthorizationTokenException.class)
        .hasMessageContaining("Missing");
  }

  // ── claim mismatch ─────────────────────────────────────────────────────────

  @Test
  void wrongAction_forStart_throwsAuthorizationTokenException() {
    when(config.isAuthorizationEnabled()).thenReturn(true);
    when(config.isNonceCheckEnabled()).thenReturn(false);

    String jwt = buildJwt("CANCEL", "my-proc", -1, UUID.randomUUID().toString(), futureExpiry());
    assertThatThrownBy(() -> service.authorize(headersWithAuth(jwt), startCommand("my-proc", -1)))
        .isInstanceOf(AuthorizationTokenException.class)
        .hasMessageContaining("action");
  }

  @Test
  void wrongProcessDefinitionId_throwsAuthorizationTokenException() {
    when(config.isAuthorizationEnabled()).thenReturn(true);
    when(config.isNonceCheckEnabled()).thenReturn(false);

    // Token authorises "proc-A" but command is for "proc-B"
    String jwt = buildJwt("START", "proc-A", -1, UUID.randomUUID().toString(), futureExpiry());
    assertThatThrownBy(() -> service.authorize(headersWithAuth(jwt), startCommand("proc-B", -1)))
        .isInstanceOf(AuthorizationTokenException.class)
        .hasMessageContaining("processDefinitionId");
  }

  @Test
  void wrongVersion_throwsAuthorizationTokenException() {
    when(config.isAuthorizationEnabled()).thenReturn(true);
    when(config.isNonceCheckEnabled()).thenReturn(false);

    // Token authorises version 2, command requests version 3
    String jwt = buildJwt("START", "proc", 2, UUID.randomUUID().toString(), futureExpiry());
    assertThatThrownBy(() -> service.authorize(headersWithAuth(jwt), startCommand("proc", 3)))
        .isInstanceOf(AuthorizationTokenException.class)
        .hasMessageContaining("version");
  }

  // ── nonce / replay ─────────────────────────────────────────────────────────

  @Test
  void replayedAuditId_throwsAuthorizationTokenException() {
    when(config.isAuthorizationEnabled()).thenReturn(true);
    when(config.isNonceCheckEnabled()).thenReturn(true);

    String auditId = UUID.randomUUID().toString();
    String jwt = buildJwt("START", null, -1, auditId, futureExpiry());
    Headers headers = headersWithAuth(jwt);

    // First call — accepted
    service.authorize(headers, startCommand(null, -1));

    // Second call with same auditId — must be rejected
    assertThatThrownBy(() -> service.authorize(headers, startCommand(null, -1)))
        .isInstanceOf(AuthorizationTokenException.class)
        .hasMessageContaining("Replayed");
  }

  @Test
  void nonceCheckDisabled_allowsReplayedAuditId() {
    when(config.isAuthorizationEnabled()).thenReturn(true);
    when(config.isNonceCheckEnabled()).thenReturn(false);

    String auditId = UUID.randomUUID().toString();
    String jwt = buildJwt("START", null, -1, auditId, futureExpiry());
    Headers headers = headersWithAuth(jwt);

    service.authorize(headers, startCommand(null, -1));
    // Should not throw
    service.authorize(headers, startCommand(null, -1));
  }

  // ── helpers ────────────────────────────────────────────────────────────────

  private String buildJwt(
      String action, String processDefinitionId, int version, String auditId, Date expiry) {
    var builder =
        Jwts.builder()
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
    headers.add("X-TaktX-Authorization", jwt.getBytes(java.nio.charset.StandardCharsets.UTF_8));
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
