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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.taktx.dto.KeyRole;
import io.taktx.dto.SigningKeyDTO;
import io.taktx.dto.SigningKeyDTO.KeyStatus;
import io.taktx.engine.config.TaktConfiguration;
import io.taktx.security.AuthorizationTokenException;
import io.taktx.security.KeyTrustPolicy;
import java.nio.charset.StandardCharsets;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.StoreQueryParameters;
import org.apache.kafka.streams.state.ReadOnlyKeyValueStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link VerificationCore}.
 *
 * <p>Covers: null header, unknown key, revoked key, role mismatch, valid CLIENT key, valid ENGINE
 * key, ENGINE-role key satisfying CLIENT requirement, keyId extraction from dotted header value,
 * and resolveKey null-on-store-error.
 */
@SuppressWarnings("unchecked")
class VerificationCoreTest {

  private static final String CLIENT_KEY_ID = "client-key-001";
  private static final String ENGINE_KEY_ID = "engine-key-001";
  private static final String REVOKED_KEY_ID = "revoked-key-001";

  private TaktConfiguration config;
  private KafkaStreams kafkaStreams;
  private KeyTrustPolicy keyTrustPolicy;
  private ReadOnlyKeyValueStore<String, SigningKeyDTO> store;

  @BeforeEach
  void setUp() {
    config = mock(TaktConfiguration.class);
    kafkaStreams = mock(KafkaStreams.class);
    keyTrustPolicy = mock(KeyTrustPolicy.class);
    store = (ReadOnlyKeyValueStore<String, SigningKeyDTO>) mock(ReadOnlyKeyValueStore.class);

    when(config.getPrefixed(anyString())).thenReturn("default.taktx-signing-keys");
    when(kafkaStreams.store(
            org.mockito.ArgumentMatchers
                .<StoreQueryParameters<ReadOnlyKeyValueStore<String, SigningKeyDTO>>>any()))
        .thenReturn(store);

    // CLIENT key
    when(store.get(CLIENT_KEY_ID))
        .thenReturn(
            SigningKeyDTO.builder()
                .keyId(CLIENT_KEY_ID)
                .publicKeyBase64("dummy-client-key")
                .algorithm("Ed25519")
                .owner("billing-worker")
                .role(KeyRole.CLIENT)
                .status(KeyStatus.ACTIVE)
                .build());

    // ENGINE key
    when(store.get(ENGINE_KEY_ID))
        .thenReturn(
            SigningKeyDTO.builder()
                .keyId(ENGINE_KEY_ID)
                .publicKeyBase64("dummy-engine-key")
                .algorithm("Ed25519")
                .owner("engine")
                .role(KeyRole.ENGINE)
                .status(KeyStatus.ACTIVE)
                .build());

    // REVOKED key
    when(store.get(REVOKED_KEY_ID))
        .thenReturn(
            SigningKeyDTO.builder()
                .keyId(REVOKED_KEY_ID)
                .publicKeyBase64("dummy-revoked-key")
                .algorithm("Ed25519")
                .owner("old-worker")
                .role(KeyRole.CLIENT)
                .status(KeyStatus.REVOKED)
                .build());

    // Default trust-policy: trust CLIENT for CLIENT, trust ENGINE for both CLIENT+ENGINE
    when(keyTrustPolicy.isTrustedForRole(any(), eq(KeyRole.CLIENT))).thenReturn(false);
    when(keyTrustPolicy.isTrustedForRole(any(), eq(KeyRole.ENGINE))).thenReturn(false);
    when(keyTrustPolicy.isTrustedForRole(
            org.mockito.ArgumentMatchers.argThat(
                k -> k != null && KeyRole.CLIENT == k.effectiveRole()),
            eq(KeyRole.CLIENT)))
        .thenReturn(true);
    when(keyTrustPolicy.isTrustedForRole(
            org.mockito.ArgumentMatchers.argThat(
                k -> k != null && KeyRole.ENGINE == k.effectiveRole()),
            eq(KeyRole.CLIENT)))
        .thenReturn(true);
    when(keyTrustPolicy.isTrustedForRole(
            org.mockito.ArgumentMatchers.argThat(
                k -> k != null && KeyRole.ENGINE == k.effectiveRole()),
            eq(KeyRole.ENGINE)))
        .thenReturn(true);
  }

  private VerificationCore core() {
    return new VerificationCore(config, kafkaStreams, keyTrustPolicy);
  }

  // ── resolveKey ───────────────────────────────────────────────────────────────

  @Test
  void resolveKey_knownKey_returnsEntry() {
    assertThat(core().resolveKey(CLIENT_KEY_ID)).isNotNull();
    assertThat(core().resolveKey(CLIENT_KEY_ID).getKeyId()).isEqualTo(CLIENT_KEY_ID);
  }

  @Test
  void resolveKey_unknownKey_returnsNull() {
    when(store.get("unknown")).thenReturn(null);
    assertThat(core().resolveKey("unknown")).isNull();
  }

  @Test
  void resolveKey_revokedKey_returnsEntryWithRevokedStatus() {
    SigningKeyDTO entry = core().resolveKey(REVOKED_KEY_ID);
    assertThat(entry).isNotNull();
    assertThat(entry.getStatus()).isEqualTo(KeyStatus.REVOKED);
  }

  @Test
  void resolveKey_storeThrows_returnsNull() {
    when(kafkaStreams.store(any())).thenThrow(new RuntimeException("store unavailable"));
    // Construct a new core so the lazy store init triggers on the next call
    VerificationCore freshCore = new VerificationCore(config, kafkaStreams, keyTrustPolicy);
    assertThat(freshCore.resolveKey(CLIENT_KEY_ID)).isNull();
  }

  // ── verify — header null / missing ───────────────────────────────────────────

  @Test
  void verify_nullHeader_throws() {
    assertThatThrownBy(() -> core().verify(null, KeyRole.CLIENT))
        .isInstanceOf(AuthorizationTokenException.class)
        .hasMessageContaining("Missing required");
  }

  @Test
  void verify_headerWithNullValue_throws() {
    Header header = new RecordHeader(VerificationCore.SIG_HEADER, (byte[]) null);
    assertThatThrownBy(() -> core().verify(header, KeyRole.CLIENT))
        .isInstanceOf(AuthorizationTokenException.class)
        .hasMessageContaining("Missing required");
  }

  // ── verify — unknown key ──────────────────────────────────────────────────────

  @Test
  void verify_unknownKey_throws() {
    when(store.get("ghost-key")).thenReturn(null);
    Header header = sigHeader("ghost-key");
    assertThatThrownBy(() -> core().verify(header, KeyRole.CLIENT))
        .isInstanceOf(AuthorizationTokenException.class)
        .hasMessageContaining("Unknown Ed25519 keyId")
        .hasMessageContaining("ghost-key");
  }

  // ── verify — revoked key ──────────────────────────────────────────────────────

  @Test
  void verify_revokedKey_throws() {
    Header header = sigHeader(REVOKED_KEY_ID);
    assertThatThrownBy(() -> core().verify(header, KeyRole.CLIENT))
        .isInstanceOf(AuthorizationTokenException.class)
        .hasMessageContaining("Revoked")
        .hasMessageContaining(REVOKED_KEY_ID);
  }

  // ── verify — role mismatch ────────────────────────────────────────────────────

  @Test
  void verify_clientKeyRequiresEngine_throws() {
    // CLIENT key cannot satisfy ENGINE requirement
    Header header = sigHeader(CLIENT_KEY_ID);
    assertThatThrownBy(() -> core().verify(header, KeyRole.ENGINE))
        .isInstanceOf(AuthorizationTokenException.class)
        .hasMessageContaining("not trusted for required role")
        .hasMessageContaining("ENGINE");
  }

  // ── verify — success: CLIENT key ─────────────────────────────────────────────

  @Test
  void verify_clientKey_returnsContextWithClientRole() {
    Header header = sigHeader(CLIENT_KEY_ID);
    VerifiedMessageContext ctx = core().verify(header, KeyRole.CLIENT);

    assertThat(ctx.keyId()).isEqualTo(CLIENT_KEY_ID);
    assertThat(ctx.role()).isEqualTo(KeyRole.CLIENT);
    assertThat(ctx.signatureValid()).isTrue();
    assertThat(ctx.key()).isNotNull();
    assertThat(ctx.key().getOwner()).isEqualTo("billing-worker");
  }

  // ── verify — success: ENGINE key with CLIENT requirement ─────────────────────

  @Test
  void verify_engineKeyForClientRequirement_returnsEngineRole() {
    // ENGINE role satisfies CLIENT requirement (trust hierarchy: PLATFORM ⊇ ENGINE ⊇ CLIENT)
    Header header = sigHeader(ENGINE_KEY_ID);
    VerifiedMessageContext ctx = core().verify(header, KeyRole.CLIENT);

    assertThat(ctx.keyId()).isEqualTo(ENGINE_KEY_ID);
    assertThat(ctx.role()).isEqualTo(KeyRole.ENGINE);
    assertThat(ctx.signatureValid()).isTrue();
  }

  // ── verify — success: ENGINE key with ENGINE requirement ──────────────────────

  @Test
  void verify_engineKey_returnsContextWithEngineRole() {
    Header header = sigHeader(ENGINE_KEY_ID);
    VerifiedMessageContext ctx = core().verify(header, KeyRole.ENGINE);

    assertThat(ctx.keyId()).isEqualTo(ENGINE_KEY_ID);
    assertThat(ctx.role()).isEqualTo(KeyRole.ENGINE);
    assertThat(ctx.signatureValid()).isTrue();
    assertThat(ctx.key().getOwner()).isEqualTo("engine");
  }

  // ── extractKeyId ─────────────────────────────────────────────────────────────

  @Test
  void extractKeyId_dottedValue_returnsBeforeDot() {
    Header header = raw("my-key-id.base64signaturehere==");
    assertThat(VerificationCore.extractKeyId(header)).isEqualTo("my-key-id");
  }

  @Test
  void extractKeyId_noDot_returnsFullValue() {
    Header header = raw("justakeyid");
    assertThat(VerificationCore.extractKeyId(header)).isEqualTo("justakeyid");
  }

  @Test
  void verify_dottedSigHeader_extractsKeyIdCorrectly() {
    // Header value is "CLIENT_KEY_ID.somepayload" — keyId extraction must strip after first dot
    Header header = raw(CLIENT_KEY_ID + ".AAAAAAAAAAAAAAAA==");
    VerifiedMessageContext ctx = core().verify(header, KeyRole.CLIENT);
    assertThat(ctx.keyId()).isEqualTo(CLIENT_KEY_ID);
  }

  // ── helpers ───────────────────────────────────────────────────────────────────

  /** Builds a sig header whose value is just the keyId (no signature bytes). */
  private static Header sigHeader(String keyId) {
    return raw(keyId);
  }

  private static Header raw(String value) {
    return new RecordHeader(VerificationCore.SIG_HEADER, value.getBytes(StandardCharsets.UTF_8));
  }
}
