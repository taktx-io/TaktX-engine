/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.security.KeyPair;
import org.junit.jupiter.api.Test;

class SigningKeyGeneratorTest {

  @Test
  void generate_returnsNonNullKeyPair() {
    KeyPair kp = SigningKeyGenerator.generate();
    assertThat(kp).isNotNull();
    assertThat(kp.getPrivate()).isNotNull();
    assertThat(kp.getPublic()).isNotNull();
  }

  @Test
  void generate_producesEd25519Keys() {
    KeyPair kp = SigningKeyGenerator.generate();
    assertThat(kp.getPrivate().getAlgorithm()).isEqualTo("EdDSA");
    assertThat(kp.getPublic().getAlgorithm()).isEqualTo("EdDSA");
  }

  @Test
  void encodePublicKey_returnsValidBase64() {
    KeyPair kp = SigningKeyGenerator.generate();
    String encoded = SigningKeyGenerator.encodePublicKey(kp.getPublic());
    assertThat(encoded).isNotBlank();
    // Should decode without error
    byte[] decoded = java.util.Base64.getDecoder().decode(encoded);
    assertThat(decoded).hasSizeGreaterThan(0);
  }

  @Test
  void encodePrivateKey_returnsValidBase64() {
    KeyPair kp = SigningKeyGenerator.generate();
    String encoded = SigningKeyGenerator.encodePrivateKey(kp.getPrivate());
    assertThat(encoded).isNotBlank();
    byte[] decoded = java.util.Base64.getDecoder().decode(encoded);
    assertThat(decoded).hasSizeGreaterThan(0);
  }

  @Test
  void encodedKeys_canBeUsedByEd25519Service() {
    KeyPair kp = SigningKeyGenerator.generate();
    String priv = SigningKeyGenerator.encodePrivateKey(kp.getPrivate());
    String pub = SigningKeyGenerator.encodePublicKey(kp.getPublic());

    byte[] data = "test payload".getBytes();
    byte[] sig = Ed25519Service.sign(data, priv);
    assertThat(Ed25519Service.verify(data, sig, pub)).isTrue();
  }

  @Test
  void twoGeneratedKeyPairs_areDifferent() {
    KeyPair kp1 = SigningKeyGenerator.generate();
    KeyPair kp2 = SigningKeyGenerator.generate();
    assertThat(kp1.getPublic().getEncoded()).isNotEqualTo(kp2.getPublic().getEncoded());
  }
}
