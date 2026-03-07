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

import io.taktx.engine.config.TaktConfiguration;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PublicKeyProviderTest {

  private TaktConfiguration config;

  @BeforeEach
  void setUp() {
    config = mock(TaktConfiguration.class);
  }

  @Test
  void init_disabled_keyRemainsNull() {
    when(config.isAuthorizationEnabled()).thenReturn(false);
    PublicKeyProvider provider = new PublicKeyProvider(config);
    provider.init();
    assertThat(provider.isReady()).isFalse();
    assertThat(provider.getPlatformKey()).isNull();
  }

  @Test
  void init_enabled_validKey_isReady() throws Exception {
    KeyPair kp = KeyPairGenerator.getInstance("RSA").generateKeyPair();
    String base64Key = Base64.getEncoder().encodeToString(kp.getPublic().getEncoded());

    when(config.isAuthorizationEnabled()).thenReturn(true);
    when(config.getPlatformPublicKeyBase64()).thenReturn(Optional.of(base64Key));

    PublicKeyProvider provider = new PublicKeyProvider(config);
    provider.init();

    assertThat(provider.isReady()).isTrue();
    assertThat(provider.getPlatformKey()).isNotNull();
    assertThat(provider.getPlatformKey().getAlgorithm()).isEqualTo("RSA");
  }

  @Test
  void init_enabled_missingKey_throwsIllegalState() {
    when(config.isAuthorizationEnabled()).thenReturn(true);
    when(config.getPlatformPublicKeyBase64()).thenReturn(Optional.empty());

    PublicKeyProvider provider = new PublicKeyProvider(config);
    assertThatThrownBy(provider::init)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("TAKTX_PLATFORM_PUBLIC_KEY");
  }

  @Test
  void init_enabled_blankKey_throwsIllegalState() {
    when(config.isAuthorizationEnabled()).thenReturn(true);
    when(config.getPlatformPublicKeyBase64()).thenReturn(Optional.of("   "));

    PublicKeyProvider provider = new PublicKeyProvider(config);
    assertThatThrownBy(provider::init)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("TAKTX_PLATFORM_PUBLIC_KEY");
  }

  @Test
  void init_enabled_invalidBase64_throwsIllegalState() {
    when(config.isAuthorizationEnabled()).thenReturn(true);
    when(config.getPlatformPublicKeyBase64()).thenReturn(Optional.of("not-valid-key-bytes!!!"));

    PublicKeyProvider provider = new PublicKeyProvider(config);
    assertThatThrownBy(provider::init)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("TAKTX_PLATFORM_PUBLIC_KEY");
  }
}
