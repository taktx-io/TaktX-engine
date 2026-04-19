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

import io.taktx.engine.config.TaktConfiguration;
import io.taktx.security.AnchoredKeyTrustPolicy;
import io.taktx.security.KeyTrustPolicy;
import io.taktx.security.OpenKeyTrustPolicy;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;
import org.junit.jupiter.api.Test;

class KeyTrustPolicyProducerTest {

  @Test
  void productionMode_requiresPlatformPublicKey() {
    TaktConfiguration config = mock(TaktConfiguration.class);
    when(config.isSecurityProductionMode()).thenReturn(true);
    when(config.getPlatformPublicKey()).thenReturn(null);

    KeyTrustPolicyProducer producer = new KeyTrustPolicyProducer(config);

    assertThatThrownBy(producer::keyTrustPolicy)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("production-mode=true")
        .hasMessageContaining("TAKTX_PLATFORM_PUBLIC_KEY");
  }

  @Test
  void communityMode_withoutPlatformPublicKey_usesOpenTrustPolicy() {
    TaktConfiguration config = mock(TaktConfiguration.class);
    when(config.isSecurityProductionMode()).thenReturn(false);
    when(config.getPlatformPublicKey()).thenReturn(null);

    KeyTrustPolicy policy = new KeyTrustPolicyProducer(config).keyTrustPolicy();

    assertThat(policy).isInstanceOf(OpenKeyTrustPolicy.class);
  }

  @Test
  void productionMode_withPlatformPublicKey_usesAnchoredTrustPolicy() throws Exception {
    TaktConfiguration config = mock(TaktConfiguration.class);
    when(config.isSecurityProductionMode()).thenReturn(true);
    when(config.getPlatformPublicKey()).thenReturn(validPlatformPublicKeyBase64());

    KeyTrustPolicy policy = new KeyTrustPolicyProducer(config).keyTrustPolicy();

    assertThat(policy).isInstanceOf(AnchoredKeyTrustPolicy.class);
  }

  private static String validPlatformPublicKeyBase64() throws Exception {
    KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
    generator.initialize(2048);
    KeyPair keyPair = generator.generateKeyPair();
    return Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded());
  }
}
