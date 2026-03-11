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
import java.security.KeyPair;
import java.util.Base64;
import java.util.List;
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

    when(kafkaStreams.store(any())).thenReturn(configStore);
    when(config.getPrefixed(any())).thenReturn("default.taktx-configuration");

    service = new MessageSigningService(config, kafkaStreams, keyProvider);
  }

  @Test
  void signingDisabled_locally_returnsNull() {
    when(config.isSigningEnabled()).thenReturn(false);
    assertThat(service.signToHeaderValue(PAYLOAD)).isNull();
  }

  @Test
  void signingEnabled_but_globalConfigNull_returnsNull() {
    when(config.isSigningEnabled()).thenReturn(true);
    when(configStore.get("config")).thenReturn(null);
    assertThat(service.signToHeaderValue(PAYLOAD)).isNull();
  }

  @Test
  void signingEnabled_but_globalConfigDisabled_returnsNull() {
    when(config.isSigningEnabled()).thenReturn(true);
    when(configStore.get("config")).thenReturn(configEvent(false, KEY_ID));
    assertThat(service.signToHeaderValue(PAYLOAD)).isNull();
  }

  @Test
  void signingEnabled_noSigningKeyId_returnsNull() {
    when(config.isSigningEnabled()).thenReturn(true);
    when(configStore.get("config")).thenReturn(configEvent(true, null));
    assertThat(service.signToHeaderValue(PAYLOAD)).isNull();
  }

  @Test
  void signingEnabled_keyProviderHasNoMatchingKey_returnsNull() {
    when(config.isSigningEnabled()).thenReturn(true);
    when(configStore.get("config")).thenReturn(configEvent(true, KEY_ID));
    when(keyProvider.hasKey(KEY_ID)).thenReturn(false);
    assertThat(service.signToHeaderValue(PAYLOAD)).isNull();
  }

  @Test
  void signingEnabled_returnsHeaderValue() {
    when(config.isSigningEnabled()).thenReturn(true);
    when(configStore.get("config")).thenReturn(configEvent(true, KEY_ID));
    when(keyProvider.hasKey(KEY_ID)).thenReturn(true);
    when(keyProvider.getPrivateKey(KEY_ID)).thenReturn(privateKeyBase64);

    assertThat(service.signToHeaderValue(PAYLOAD)).isNotNull();
  }

  @Test
  void headerValue_hasCorrectFormat_keyIdDotBase64() {
    when(config.isSigningEnabled()).thenReturn(true);
    when(configStore.get("config")).thenReturn(configEvent(true, KEY_ID));
    when(keyProvider.hasKey(KEY_ID)).thenReturn(true);
    when(keyProvider.getPrivateKey(KEY_ID)).thenReturn(privateKeyBase64);

    String headerValue = service.signToHeaderValue(PAYLOAD);

    assertThat(headerValue).startsWith(KEY_ID + ".");
    String b64Part = headerValue.substring(KEY_ID.length() + 1);
    assertThat(Base64.getDecoder().decode(b64Part)).hasSize(64);
  }

  @Test
  void headerValue_isVerifiableWithPublicKey() {
    when(config.isSigningEnabled()).thenReturn(true);
    when(configStore.get("config")).thenReturn(configEvent(true, KEY_ID));
    when(keyProvider.hasKey(KEY_ID)).thenReturn(true);
    when(keyProvider.getPrivateKey(KEY_ID)).thenReturn(privateKeyBase64);

    String headerValue = service.signToHeaderValue(PAYLOAD);
    byte[] sigBytes = Base64.getDecoder().decode(headerValue.substring(KEY_ID.length() + 1));

    assertThat(Ed25519Service.verify(PAYLOAD, sigBytes, publicKeyBase64)).isTrue();
  }

  @Test
  void multipleCallsWithSameKey_eachHasValidSignature() {
    when(config.isSigningEnabled()).thenReturn(true);
    when(configStore.get("config")).thenReturn(configEvent(true, KEY_ID));
    when(keyProvider.hasKey(KEY_ID)).thenReturn(true);
    when(keyProvider.getPrivateKey(KEY_ID)).thenReturn(privateKeyBase64);

    for (int i = 0; i < 5; i++) {
      byte[] payload = ("payload-" + i).getBytes();
      String hv = service.signToHeaderValue(payload);
      assertThat(hv).isNotNull();
      byte[] sig = Base64.getDecoder().decode(hv.substring(KEY_ID.length() + 1));
      assertThat(Ed25519Service.verify(payload, sig, publicKeyBase64)).isTrue();
    }
  }

  // ── helpers ────────────────────────────────────────────────────────────────

  private ConfigurationEventDTO configEvent(boolean signingEnabled, String signingKeyId) {
    GlobalConfigurationDTO globalConfig =
        GlobalConfigurationDTO.builder()
            .signingEnabled(signingEnabled)
            .signingKeyId(signingKeyId)
            .trustedKeyIds(signingKeyId != null ? List.of(signingKeyId) : List.of())
            .build();
    return ConfigurationEventDTO.builder()
        .eventType(ConfigurationEventDTO.ConfigurationEventType.CONFIGURATION_UPDATE)
        .configuration(globalConfig)
        .timestamp(java.time.Instant.now())
        .build();
  }
}
