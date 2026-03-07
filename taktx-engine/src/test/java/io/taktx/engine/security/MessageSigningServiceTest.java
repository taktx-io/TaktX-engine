/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks All rights reserved.
 * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 * Free use is permitted with up to 3 Kafka partitions per topic. See LICENSE file for details.
 * For commercial use or more partitions and features, contact [https://www.taktx.io/contact].
 */
package io.taktx.engine.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.taktx.dto.ConfigurationEventDTO;
import io.taktx.dto.GlobalConfigurationDTO;
import io.taktx.engine.config.TaktConfiguration;
import io.taktx.security.Ed25519Service;
import io.taktx.security.SigningKeyGenerator;
import io.taktx.security.SigningKeyProvider;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.util.Base64;
import java.util.List;
import org.apache.kafka.common.header.Headers;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.state.ReadOnlyKeyValueStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@SuppressWarnings("unchecked")
class MessageSigningServiceTest {

  private static final String KEY_ID = "test-key-1";
  private static final byte[] PAYLOAD = "cbor-payload-bytes".getBytes();

  private TaktConfiguration config;
  private ReadOnlyKeyValueStore<String, ConfigurationEventDTO> configStore;
  private SigningKeyProvider keyProvider;
  private MessageSigningService service;

  private String privateKeyBase64;
  private String publicKeyBase64;

  @BeforeEach
  void setUp() {
    KeyPair kp = SigningKeyGenerator.generate();
    privateKeyBase64 = SigningKeyGenerator.encodePrivateKey(kp.getPrivate());
    publicKeyBase64 = SigningKeyGenerator.encodePublicKey(kp.getPublic());

    config = mock(TaktConfiguration.class);
    KafkaStreams kafkaStreams = mock(KafkaStreams.class);
    configStore = mock(ReadOnlyKeyValueStore.class);
    keyProvider = mock(SigningKeyProvider.class);

    // Wire the KafkaStreams mock to return our configStore
    when(kafkaStreams.store(any())).thenReturn(configStore);
    when(config.getPrefixed(any())).thenReturn("default.taktx-configuration");

    service = new MessageSigningService(config, kafkaStreams, keyProvider);
  }

  @Test
  void signingDisabled_locally_noHeaderAdded() {
    when(config.isSigningEnabled()).thenReturn(false);

    Headers headers = new RecordHeaders();
    service.signIfEnabled(headers, PAYLOAD);

    assertThat(headers.lastHeader("X-TaktX-Signature")).isNull();
  }

  @Test
  void signingEnabled_but_globalConfigNull_noHeaderAdded() {
    when(config.isSigningEnabled()).thenReturn(true);
    when(configStore.get("config")).thenReturn(null);

    Headers headers = new RecordHeaders();
    service.signIfEnabled(headers, PAYLOAD);

    assertThat(headers.lastHeader("X-TaktX-Signature")).isNull();
  }

  @Test
  void signingEnabled_but_globalConfigDisabled_noHeaderAdded() {
    when(config.isSigningEnabled()).thenReturn(true);
    when(configStore.get("config")).thenReturn(configEvent(false, List.of(KEY_ID)));

    Headers headers = new RecordHeaders();
    service.signIfEnabled(headers, PAYLOAD);

    assertThat(headers.lastHeader("X-TaktX-Signature")).isNull();
  }

  @Test
  void signingEnabled_noActiveKeyIds_noHeaderAdded() {
    when(config.isSigningEnabled()).thenReturn(true);
    when(configStore.get("config")).thenReturn(configEvent(true, List.of()));

    Headers headers = new RecordHeaders();
    service.signIfEnabled(headers, PAYLOAD);

    assertThat(headers.lastHeader("X-TaktX-Signature")).isNull();
  }

  @Test
  void signingEnabled_keyProviderHasNoMatchingKey_noHeaderAdded() {
    when(config.isSigningEnabled()).thenReturn(true);
    when(configStore.get("config")).thenReturn(configEvent(true, List.of(KEY_ID)));
    when(keyProvider.hasKey(KEY_ID)).thenReturn(false);

    Headers headers = new RecordHeaders();
    service.signIfEnabled(headers, PAYLOAD);

    assertThat(headers.lastHeader("X-TaktX-Signature")).isNull();
  }

  @Test
  void signingEnabled_addsSignatureHeader() {
    when(config.isSigningEnabled()).thenReturn(true);
    when(configStore.get("config")).thenReturn(configEvent(true, List.of(KEY_ID)));
    when(keyProvider.hasKey(KEY_ID)).thenReturn(true);
    when(keyProvider.getPrivateKey(KEY_ID)).thenReturn(privateKeyBase64);

    Headers headers = new RecordHeaders();
    service.signIfEnabled(headers, PAYLOAD);

    assertThat(headers.lastHeader("X-TaktX-Signature")).isNotNull();
  }

  @Test
  void signatureHeader_hasCorrectFormat_keyIdDotBase64() {
    when(config.isSigningEnabled()).thenReturn(true);
    when(configStore.get("config")).thenReturn(configEvent(true, List.of(KEY_ID)));
    when(keyProvider.hasKey(KEY_ID)).thenReturn(true);
    when(keyProvider.getPrivateKey(KEY_ID)).thenReturn(privateKeyBase64);

    Headers headers = new RecordHeaders();
    service.signIfEnabled(headers, PAYLOAD);

    String headerValue =
        new String(headers.lastHeader("X-TaktX-Signature").value(), StandardCharsets.UTF_8);
    assertThat(headerValue).startsWith(KEY_ID + ".");
    // Second part must be valid base64
    String b64Part = headerValue.substring(KEY_ID.length() + 1);
    assertThat(Base64.getDecoder().decode(b64Part)).hasSize(64); // Ed25519 sig is always 64 bytes
  }

  @Test
  void signatureHeader_isVerifiableWithPublicKey() {
    when(config.isSigningEnabled()).thenReturn(true);
    when(configStore.get("config")).thenReturn(configEvent(true, List.of(KEY_ID)));
    when(keyProvider.hasKey(KEY_ID)).thenReturn(true);
    when(keyProvider.getPrivateKey(KEY_ID)).thenReturn(privateKeyBase64);

    Headers headers = new RecordHeaders();
    service.signIfEnabled(headers, PAYLOAD);

    String headerValue =
        new String(headers.lastHeader("X-TaktX-Signature").value(), StandardCharsets.UTF_8);
    byte[] sigBytes = Base64.getDecoder().decode(headerValue.substring(KEY_ID.length() + 1));

    assertThat(Ed25519Service.verify(PAYLOAD, sigBytes, publicKeyBase64)).isTrue();
  }

  @Test
  void multipleCallsWithSameKey_eachHasValidSignature() {
    when(config.isSigningEnabled()).thenReturn(true);
    when(configStore.get("config")).thenReturn(configEvent(true, List.of(KEY_ID)));
    when(keyProvider.hasKey(KEY_ID)).thenReturn(true);
    when(keyProvider.getPrivateKey(KEY_ID)).thenReturn(privateKeyBase64);

    for (int i = 0; i < 5; i++) {
      byte[] payload = ("payload-" + i).getBytes();
      Headers headers = new RecordHeaders();
      service.signIfEnabled(headers, payload);

      String hv =
          new String(headers.lastHeader("X-TaktX-Signature").value(), StandardCharsets.UTF_8);
      byte[] sig = Base64.getDecoder().decode(hv.substring(KEY_ID.length() + 1));
      assertThat(Ed25519Service.verify(payload, sig, publicKeyBase64)).isTrue();
    }
  }

  // ── helpers ────────────────────────────────────────────────────────────────

  private ConfigurationEventDTO configEvent(boolean signingEnabled, List<String> activeKeyIds) {
    GlobalConfigurationDTO globalConfig =
        GlobalConfigurationDTO.builder()
            .signingEnabled(signingEnabled)
            .activeKeyIds(activeKeyIds)
            .build();
    return ConfigurationEventDTO.builder()
        .eventType(ConfigurationEventDTO.ConfigurationEventType.CONFIGURATION_UPDATE)
        .configuration(globalConfig)
        .timestamp(java.time.Instant.now())
        .build();
  }
}
