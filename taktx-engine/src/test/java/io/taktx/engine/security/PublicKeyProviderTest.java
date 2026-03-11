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

import io.taktx.dto.SigningKeyDTO;
import io.taktx.engine.config.TaktConfiguration;
import io.taktx.security.AuthorizationTokenException;
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

  @SuppressWarnings("unchecked")
  private ReadOnlyKeyValueStore<String, SigningKeyDTO> store;

  @BeforeEach
  @SuppressWarnings("unchecked")
  void setUp() {
    config = mock(TaktConfiguration.class);
    kafkaStreams = mock(KafkaStreams.class);
    store = mock(ReadOnlyKeyValueStore.class);
    when(kafkaStreams.store(org.mockito.ArgumentMatchers.any(StoreQueryParameters.class)))
        .thenReturn(store);
    when(config.getPrefixed(org.mockito.ArgumentMatchers.anyString()))
        .thenReturn("default.taktx-signing-keys");
  }

  private PublicKeyProvider provider() {
    return new PublicKeyProvider(config, kafkaStreams);
  }

  // ── getKey — null / blank kid ─────────────────────────────────────────────

  @Test
  void getKey_nullKid_throws() {
    assertThatThrownBy(() -> provider().getKey(null))
        .isInstanceOf(AuthorizationTokenException.class)
        .hasMessageContaining("kid");
  }

  @Test
  void getKey_blankKid_throws() {
    assertThatThrownBy(() -> provider().getKey("  "))
        .isInstanceOf(AuthorizationTokenException.class)
        .hasMessageContaining("kid");
  }

  // ── getKey — KTable misses ────────────────────────────────────────────────

  @Test
  void getKey_noKTableEntry_throws() {
    when(store.get(KID)).thenReturn(null);

    assertThatThrownBy(() -> provider().getKey(KID))
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
            .status(SigningKeyDTO.KeyStatus.REVOKED)
            .build();

    when(store.get(KID)).thenReturn(revoked);

    assertThatThrownBy(() -> provider().getKey(KID))
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
                .status(SigningKeyDTO.KeyStatus.ACTIVE)
                .build());
    when(store.get(kid2))
        .thenReturn(
            SigningKeyDTO.builder()
                .keyId(kid2)
                .publicKeyBase64(Base64.getEncoder().encodeToString(kp2.getPublic().getEncoded()))
                .algorithm("RSA")
                .owner("platform")
                .status(SigningKeyDTO.KeyStatus.ACTIVE)
                .build());

    PublicKeyProvider p = provider();
    assertThat(p.getKey(kid1)).isEqualTo(kp1.getPublic());
    assertThat(p.getKey(kid2)).isEqualTo(kp2.getPublic());
  }
}
