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
  void noPlatformPublicKey_usesOpenTrustPolicy() {
    TaktConfiguration config = mock(TaktConfiguration.class);
    when(config.getPlatformPublicKey()).thenReturn(null);

    KeyTrustPolicy policy = new KeyTrustPolicyProducer(config).keyTrustPolicy();

    assertThat(policy).isInstanceOf(OpenKeyTrustPolicy.class);
  }

  @Test
  void platformPublicKeyPresent_withStableSigningSource_usesAnchoredTrustPolicy() throws Exception {
    TaktConfiguration config = mock(TaktConfiguration.class);
    when(config.getPlatformPublicKey()).thenReturn(validPlatformPublicKeyBase64());
    when(config.getSigningIdentitySourceType()).thenReturn("file");
    when(config.getEngineKeyRegistrationSignature()).thenReturn("registration-signature");

    KeyTrustPolicy policy = new KeyTrustPolicyProducer(config).keyTrustPolicy();

    assertThat(policy).isInstanceOf(AnchoredKeyTrustPolicy.class);
  }

  @Test
  void platformPublicKeyPresent_withGeneratedSigningSource_failsFast() throws Exception {
    TaktConfiguration config = mock(TaktConfiguration.class);
    when(config.getPlatformPublicKey()).thenReturn(validPlatformPublicKeyBase64());
    when(config.getSigningIdentitySourceType()).thenReturn("generated");

    assertThatThrownBy(() -> new KeyTrustPolicyProducer(config).keyTrustPolicy())
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("TAKTX_SIGNING_IDENTITY_SOURCE");
  }

  @Test
  void platformPublicKeyPresent_withMissingRegistrationSignature_failsFast() throws Exception {
    TaktConfiguration config = mock(TaktConfiguration.class);
    when(config.getPlatformPublicKey()).thenReturn(validPlatformPublicKeyBase64());
    when(config.getSigningIdentitySourceType()).thenReturn("env");
    when(config.getEngineKeyRegistrationSignature()).thenReturn(null);

    assertThatThrownBy(() -> new KeyTrustPolicyProducer(config).keyTrustPolicy())
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("TAKTX_ENGINE_KEY_REGISTRATION_SIGNATURE");
  }

  private static String validPlatformPublicKeyBase64() throws Exception {
    KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
    generator.initialize(2048);
    KeyPair keyPair = generator.generateKeyPair();
    return Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded());
  }
}
