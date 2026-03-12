/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks All rights reserved.
 * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 * Free use is permitted with up to 3 Kafka partitions per topic. See LICENSE file for details.
 * For commercial use or more partitions and features, contact [https://www.taktx.io/contact].
 */
package io.taktx.engine.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.taktx.dto.GlobalConfigurationDTO;
import io.taktx.engine.config.GlobalConfigStore;
import io.taktx.engine.config.TaktConfiguration;
import io.taktx.security.Ed25519Service;
import io.taktx.security.SigningKeyGenerator;
import io.taktx.security.SigningKeyProvider;
import java.security.KeyPair;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MessageSigningServiceTest {

  private static final String KEY_ID = "test-key-1";
  private static final byte[] PAYLOAD = "cbor-payload-bytes".getBytes();

  private TaktConfiguration config;
  private GlobalConfigStore globalConfigStore;
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
    globalConfigStore = new GlobalConfigStore();
    keyProvider = mock(SigningKeyProvider.class);

    when(config.getSigningKeyId()).thenReturn(Optional.empty());

    service = new MessageSigningService(config, keyProvider);
    // inject globalConfigStore via the package-private field accessor for tests
    // (the test constructor leaves globalConfigStore null, so we use a helper method)
  }

  /** Helper: build a service that reads from globalConfigStore and uses the keyProvider mock. */
  private MessageSigningService serviceWithConfigStore(GlobalConfigStore store) {
    return new MessageSigningService(config, store, keyProvider);
  }

  @Test
  void signingDisabled_locally_returnsNull() {
    when(config.isSigningEnabled()).thenReturn(false);
    assertThat(service.signToHeaderValue(PAYLOAD)).isNull();
  }

  @Test
  void signingEnabled_but_globalConfigNull_returnsNull() {
    MessageSigningService svc = serviceWithConfigStore(globalConfigStore);
    when(config.isSigningEnabled()).thenReturn(true);
    // globalConfigStore has no config → falls back to env, which is empty
    assertThat(svc.signToHeaderValue(PAYLOAD)).isNull();
  }

  @Test
  void signingEnabled_but_globalConfigDisabled_returnsNull() {
    MessageSigningService svc = serviceWithConfigStore(globalConfigStore);
    when(config.isSigningEnabled()).thenReturn(true);
    globalConfigStore.update(globalConfig(false, KEY_ID));
    assertThat(svc.signToHeaderValue(PAYLOAD)).isNull();
  }

  @Test
  void signingEnabled_noSigningKeyId_returnsNull() {
    MessageSigningService svc = serviceWithConfigStore(globalConfigStore);
    when(config.isSigningEnabled()).thenReturn(true);
    globalConfigStore.update(globalConfig(true, null));
    assertThat(svc.signToHeaderValue(PAYLOAD)).isNull();
  }

  @Test
  void signingEnabled_keyProviderHasNoMatchingKey_returnsNull() {
    MessageSigningService svc = serviceWithConfigStore(globalConfigStore);
    when(config.isSigningEnabled()).thenReturn(true);
    globalConfigStore.update(globalConfig(true, KEY_ID));
    when(keyProvider.hasKey(KEY_ID)).thenReturn(false);
    assertThat(svc.signToHeaderValue(PAYLOAD)).isNull();
  }

  @Test
  void signingEnabled_returnsHeaderValue() {
    MessageSigningService svc = serviceWithConfigStore(globalConfigStore);
    when(config.isSigningEnabled()).thenReturn(true);
    globalConfigStore.update(globalConfig(true, KEY_ID));
    when(keyProvider.hasKey(KEY_ID)).thenReturn(true);
    when(keyProvider.getPrivateKey(KEY_ID)).thenReturn(privateKeyBase64);

    assertThat(svc.signToHeaderValue(PAYLOAD)).isNotNull();
  }

  @Test
  void headerValue_hasCorrectFormat_keyIdDotBase64() {
    MessageSigningService svc = serviceWithConfigStore(globalConfigStore);
    when(config.isSigningEnabled()).thenReturn(true);
    globalConfigStore.update(globalConfig(true, KEY_ID));
    when(keyProvider.hasKey(KEY_ID)).thenReturn(true);
    when(keyProvider.getPrivateKey(KEY_ID)).thenReturn(privateKeyBase64);

    String headerValue = svc.signToHeaderValue(PAYLOAD);

    assertThat(headerValue).startsWith(KEY_ID + ".");
    String b64Part = headerValue.substring(KEY_ID.length() + 1);
    assertThat(Base64.getDecoder().decode(b64Part)).hasSize(64);
  }

  @Test
  void headerValue_isVerifiableWithPublicKey() {
    MessageSigningService svc = serviceWithConfigStore(globalConfigStore);
    when(config.isSigningEnabled()).thenReturn(true);
    globalConfigStore.update(globalConfig(true, KEY_ID));
    when(keyProvider.hasKey(KEY_ID)).thenReturn(true);
    when(keyProvider.getPrivateKey(KEY_ID)).thenReturn(privateKeyBase64);

    String headerValue = svc.signToHeaderValue(PAYLOAD);
    byte[] sigBytes = Base64.getDecoder().decode(headerValue.substring(KEY_ID.length() + 1));

    assertThat(Ed25519Service.verify(PAYLOAD, sigBytes, publicKeyBase64)).isTrue();
  }

  @Test
  void multipleCallsWithSameKey_eachHasValidSignature() {
    MessageSigningService svc = serviceWithConfigStore(globalConfigStore);
    when(config.isSigningEnabled()).thenReturn(true);
    globalConfigStore.update(globalConfig(true, KEY_ID));
    when(keyProvider.hasKey(KEY_ID)).thenReturn(true);
    when(keyProvider.getPrivateKey(KEY_ID)).thenReturn(privateKeyBase64);

    for (int i = 0; i < 5; i++) {
      byte[] payload = ("payload-" + i).getBytes();
      String hv = svc.signToHeaderValue(payload);
      assertThat(hv).isNotNull();
      byte[] sig = Base64.getDecoder().decode(hv.substring(KEY_ID.length() + 1));
      assertThat(Ed25519Service.verify(payload, sig, publicKeyBase64)).isTrue();
    }
  }

  // ── helpers ────────────────────────────────────────────────────────────────

  private GlobalConfigurationDTO globalConfig(boolean signingEnabled, String signingKeyId) {
    return GlobalConfigurationDTO.builder()
        .signingEnabled(signingEnabled)
        .signingKeyId(signingKeyId)
        .trustedKeyIds(signingKeyId != null ? List.of(signingKeyId) : List.of())
        .build();
  }
}
