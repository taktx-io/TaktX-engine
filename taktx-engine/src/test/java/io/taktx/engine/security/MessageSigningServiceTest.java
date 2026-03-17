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

import io.taktx.dto.GlobalConfigurationDTO;
import io.taktx.engine.config.GlobalConfigStore;
import io.taktx.engine.config.TaktConfiguration;
import io.taktx.security.Ed25519Service;
import io.taktx.security.SigningIdentity;
import io.taktx.security.SigningIdentitySource;
import io.taktx.security.SigningKeyGenerator;
import io.taktx.security.StaticSigningIdentitySource;
import java.security.KeyPair;
import java.util.Base64;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MessageSigningServiceTest {

  private static final String KEY_ID = "test-key-1";
  private static final byte[] PAYLOAD = "cbor-payload-bytes".getBytes();

  private TaktConfiguration config;
  private GlobalConfigStore globalConfigStore;
  private MessageSigningService service;
  private SigningIdentitySource signingIdentitySource;

  private String publicKeyBase64;
  private KeyPair keyPair;

  @BeforeEach
  void setUp() {
    keyPair = SigningKeyGenerator.generate();
    publicKeyBase64 = SigningKeyGenerator.encodePublicKey(keyPair.getPublic());
    signingIdentitySource =
        new StaticSigningIdentitySource(
            SigningIdentity.ed25519(
                KEY_ID,
                SigningKeyGenerator.encodePrivateKey(keyPair.getPrivate()),
                publicKeyBase64));

    config = mock(TaktConfiguration.class);
    globalConfigStore = new GlobalConfigStore();
    service = new MessageSigningService(config, null, null, signingIdentitySource, false);
  }

  /**
   * Helper: build a service that reads from globalConfigStore and uses the static identity source.
   */
  private MessageSigningService serviceWithConfigStore(GlobalConfigStore store) {
    return new MessageSigningService(config, store, null, signingIdentitySource, false);
  }

  @Test
  void signingDisabled_locally_returnsNull() {
    assertThat(service.signToHeaderValue(PAYLOAD)).isNull();
  }

  @Test
  void signingEnabled_but_globalConfigNull_returnsNull() {
    MessageSigningService svc = serviceWithConfigStore(globalConfigStore);
    assertThat(svc.signToHeaderValue(PAYLOAD)).isNull();
  }

  @Test
  void signingEnabled_but_globalConfigDisabled_returnsNull() {
    MessageSigningService svc = serviceWithConfigStore(globalConfigStore);
    globalConfigStore.update(globalConfig(false));
    assertThat(svc.signToHeaderValue(PAYLOAD)).isNull();
  }

  @Test
  void signingEnabled_globalConfigPresent_returnsHeaderValue() {
    MessageSigningService svc = serviceWithConfigStore(globalConfigStore);
    globalConfigStore.update(globalConfig(true));
    assertThat(svc.signToHeaderValue(PAYLOAD)).isNotNull();
  }

  @Test
  void headerValue_hasCorrectFormat_keyIdDotBase64() {
    MessageSigningService svc = serviceWithConfigStore(globalConfigStore);
    globalConfigStore.update(globalConfig(true));

    String headerValue = svc.signToHeaderValue(PAYLOAD);

    assertThat(headerValue).startsWith(KEY_ID + ".");
    String b64Part = headerValue.substring(KEY_ID.length() + 1);
    assertThat(Base64.getDecoder().decode(b64Part)).hasSize(64);
  }

  @Test
  void headerValue_isVerifiableWithPublicKey() {
    MessageSigningService svc = serviceWithConfigStore(globalConfigStore);
    globalConfigStore.update(globalConfig(true));

    String headerValue = svc.signToHeaderValue(PAYLOAD);
    byte[] sigBytes = Base64.getDecoder().decode(headerValue.substring(KEY_ID.length() + 1));

    assertThat(Ed25519Service.verify(PAYLOAD, sigBytes, publicKeyBase64)).isTrue();
  }

  @Test
  void multipleCallsWithSameKey_eachHasValidSignature() {
    MessageSigningService svc = serviceWithConfigStore(globalConfigStore);
    globalConfigStore.update(globalConfig(true));

    for (int i = 0; i < 5; i++) {
      byte[] payload = ("payload-" + i).getBytes();
      String hv = svc.signToHeaderValue(payload);
      assertThat(hv).isNotNull();
      byte[] sig = Base64.getDecoder().decode(hv.substring(KEY_ID.length() + 1));
      assertThat(Ed25519Service.verify(payload, sig, publicKeyBase64)).isTrue();
    }
  }

  // ── helpers ────────────────────────────────────────────────────────────────

  private GlobalConfigurationDTO globalConfig(boolean signingEnabled) {
    return GlobalConfigurationDTO.builder()
        .signingEnabled(signingEnabled)
        .trustedKeyIds(List.of(KEY_ID))
        .build();
  }
}
