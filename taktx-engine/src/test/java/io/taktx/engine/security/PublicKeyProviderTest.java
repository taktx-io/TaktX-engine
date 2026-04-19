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

import io.taktx.dto.KeyRole;
import io.taktx.dto.SigningKeyDTO;
import io.taktx.dto.SigningKeyDTO.KeyStatus;
import io.taktx.engine.config.TaktConfiguration;
import io.taktx.security.AuthorizationTokenException;
import io.taktx.security.KeyTrustPolicy;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.StoreQueryParameters;
import org.apache.kafka.streams.state.ReadOnlyKeyValueStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PublicKeyProviderTest {

  private static final String KID = "platform-key-2025";

  private TaktConfiguration config;
  private KafkaStreams kafkaStreams;
  private KeyTrustPolicy keyTrustPolicy;

  private ReadOnlyKeyValueStore<String, SigningKeyDTO> store;

  @BeforeEach
  void setUp() {
    config = mock(TaktConfiguration.class);
    kafkaStreams = mock(KafkaStreams.class);
    keyTrustPolicy = mock(KeyTrustPolicy.class);
    @SuppressWarnings("unchecked")
    ReadOnlyKeyValueStore<String, SigningKeyDTO> mockedStore =
        (ReadOnlyKeyValueStore<String, SigningKeyDTO>) mock(ReadOnlyKeyValueStore.class);
    store = mockedStore;
    when(kafkaStreams.store(
            org.mockito.ArgumentMatchers
                .<StoreQueryParameters<ReadOnlyKeyValueStore<String, SigningKeyDTO>>>any()))
        .thenReturn(store);
    when(config.getPrefixed(org.mockito.ArgumentMatchers.anyString()))
        .thenReturn("default.taktx-signing-keys");
    when(keyTrustPolicy.isTrustedForRole(
            org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(KeyRole.PLATFORM)))
        .thenReturn(true);
  }

  private PublicKeyProvider provider() {
    return new PublicKeyProvider(config, kafkaStreams, keyTrustPolicy);
  }

  // ── getKey — null / blank kid ─────────────────────────────────────────────

  @Test
  void getKey_nullKid_throws() {
    PublicKeyProvider provider = provider();

    assertThatThrownBy(() -> provider.getKey(null))
        .isInstanceOf(AuthorizationTokenException.class)
        .hasMessageContaining("kid");
  }

  @Test
  void getKey_blankKid_throws() {
    PublicKeyProvider provider = provider();

    assertThatThrownBy(() -> provider.getKey("  "))
        .isInstanceOf(AuthorizationTokenException.class)
        .hasMessageContaining("kid");
  }

  // ── getKey — KTable misses ────────────────────────────────────────────────

  @Test
  void getKey_noKTableEntry_throws() {
    when(store.get(KID)).thenReturn(null);
    PublicKeyProvider provider = provider();

    assertThatThrownBy(() -> provider.getKey(KID))
        .isInstanceOf(AuthorizationTokenException.class)
        .hasMessageContaining("No signing key found")
        .hasMessageContaining(KID);
  }

  // ── getKey — REVOKED ──────────────────────────────────────────────────────

  @Test
  void getKey_revokedEntry_throws() throws Exception {
    KeyPair kp = KeyPairGenerator.getInstance("RSA").generateKeyPair();
    String base64 = Base64.getEncoder().encodeToString(kp.getPublic().getEncoded());
    SigningKeyDTO revoked =
        SigningKeyDTO.builder()
            .keyId(KID)
            .publicKeyBase64(base64)
            .algorithm("RSA")
            .owner("platform")
            .role(KeyRole.PLATFORM)
            .status(SigningKeyDTO.KeyStatus.REVOKED)
            .build();

    when(store.get(KID)).thenReturn(revoked);
    PublicKeyProvider provider = provider();

    assertThatThrownBy(() -> provider.getKey(KID))
        .isInstanceOf(AuthorizationTokenException.class)
        .hasMessageContaining("revoked")
        .hasMessageContaining(KID);
  }

  // ── getKey — ACTIVE ───────────────────────────────────────────────────────

  @Test
  void getKey_activeEntry_returnsPublicKey() throws Exception {
    KeyPair kp = KeyPairGenerator.getInstance("RSA").generateKeyPair();
    String base64 = Base64.getEncoder().encodeToString(kp.getPublic().getEncoded());
    SigningKeyDTO active =
        SigningKeyDTO.builder()
            .keyId(KID)
            .publicKeyBase64(base64)
            .algorithm("RSA")
            .owner("platform")
            .role(KeyRole.PLATFORM)
            .status(SigningKeyDTO.KeyStatus.ACTIVE)
            .build();

    when(store.get(KID)).thenReturn(active);

    var key = provider().getKey(KID);
    assertThat(key).isNotNull();
    assertThat(key.getAlgorithm()).isEqualTo("RSA");
  }

  // ── getKey — key rotation ─────────────────────────────────────────────────

  @Test
  void getKey_differentKids_returnsDifferentKeys() throws Exception {
    String kid1 = "platform-key-v1";
    String kid2 = "platform-key-v2";

    KeyPair kp1 = KeyPairGenerator.getInstance("RSA").generateKeyPair();
    KeyPair kp2 = KeyPairGenerator.getInstance("RSA").generateKeyPair();

    when(store.get(kid1))
        .thenReturn(
            SigningKeyDTO.builder()
                .keyId(kid1)
                .publicKeyBase64(Base64.getEncoder().encodeToString(kp1.getPublic().getEncoded()))
                .algorithm("RSA")
                .owner("platform")
                .role(KeyRole.PLATFORM)
                .status(SigningKeyDTO.KeyStatus.ACTIVE)
                .build());
    when(store.get(kid2))
        .thenReturn(
            SigningKeyDTO.builder()
                .keyId(kid2)
                .publicKeyBase64(Base64.getEncoder().encodeToString(kp2.getPublic().getEncoded()))
                .algorithm("RSA")
                .owner("platform")
                .role(KeyRole.PLATFORM)
                .status(SigningKeyDTO.KeyStatus.ACTIVE)
                .build());

    PublicKeyProvider p = provider();
    assertThat(p.getKey(kid1)).isEqualTo(kp1.getPublic());
    assertThat(p.getKey(kid2)).isEqualTo(kp2.getPublic());
  }

  @Test
  void getKey_clientRoleEntry_throws() throws Exception {
    KeyPair kp = KeyPairGenerator.getInstance("RSA").generateKeyPair();
    when(store.get(KID))
        .thenReturn(
            SigningKeyDTO.builder()
                .keyId(KID)
                .publicKeyBase64(Base64.getEncoder().encodeToString(kp.getPublic().getEncoded()))
                .algorithm("RSA")
                .owner("platform")
                .role(KeyRole.CLIENT)
                .status(KeyStatus.ACTIVE)
                .build());
    when(keyTrustPolicy.isTrustedForRole(
            org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(KeyRole.PLATFORM)))
        .thenReturn(false);
    PublicKeyProvider provider = provider();

    assertThatThrownBy(() -> provider.getKey(KID))
        .isInstanceOf(AuthorizationTokenException.class)
        .hasMessageContaining("not trusted as a PLATFORM JWT issuer key");
  }

  @Test
  void getKey_untrustedPlatformEntry_throws() throws Exception {
    KeyPair kp = KeyPairGenerator.getInstance("RSA").generateKeyPair();
    when(store.get(KID))
        .thenReturn(
            SigningKeyDTO.builder()
                .keyId(KID)
                .publicKeyBase64(Base64.getEncoder().encodeToString(kp.getPublic().getEncoded()))
                .algorithm("RSA")
                .owner("platform")
                .role(KeyRole.PLATFORM)
                .status(KeyStatus.ACTIVE)
                .build());
    when(keyTrustPolicy.isTrustedForRole(
            org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(KeyRole.PLATFORM)))
        .thenReturn(false);
    PublicKeyProvider provider = provider();

    assertThatThrownBy(() -> provider.getKey(KID))
        .isInstanceOf(AuthorizationTokenException.class)
        .hasMessageContaining("not trusted as a PLATFORM JWT issuer key");
  }

  @Test
  void getKey_nonRsaPlatformEntry_throws() throws Exception {
    KeyPair kp = KeyPairGenerator.getInstance("RSA").generateKeyPair();
    when(store.get(KID))
        .thenReturn(
            SigningKeyDTO.builder()
                .keyId(KID)
                .publicKeyBase64(Base64.getEncoder().encodeToString(kp.getPublic().getEncoded()))
                .algorithm("Ed25519")
                .owner("platform")
                .role(KeyRole.PLATFORM)
                .status(KeyStatus.ACTIVE)
                .build());
    PublicKeyProvider provider = provider();

    assertThatThrownBy(() -> provider.getKey(KID))
        .isInstanceOf(AuthorizationTokenException.class)
        .hasMessageContaining("not an RSA JWT issuer key")
        .hasMessageContaining("Ed25519");
  }
}
