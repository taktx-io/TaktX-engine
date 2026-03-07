/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks All rights reserved.
 * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 * Free use is permitted with up to 3 Kafka partitions per topic. See LICENSE file for details.
 * For commercial use or more partitions and features, contact [https://www.taktx.io/contact].
 */
package io.taktx.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.security.KeyPair;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class Ed25519ServiceTest {

  private String privateKeyBase64;
  private String publicKeyBase64;

  @BeforeEach
  void setUp() {
    KeyPair kp = SigningKeyGenerator.generate();
    privateKeyBase64 = SigningKeyGenerator.encodePrivateKey(kp.getPrivate());
    publicKeyBase64 = SigningKeyGenerator.encodePublicKey(kp.getPublic());
  }

  @Test
  void sign_and_verify_roundTrip() {
    byte[] data = "hello taktx".getBytes();
    byte[] sig = Ed25519Service.sign(data, privateKeyBase64);
    assertThat(Ed25519Service.verify(data, sig, publicKeyBase64)).isTrue();
  }

  @Test
  void verify_returnsFalse_forTamperedData() {
    byte[] data = "original".getBytes();
    byte[] sig = Ed25519Service.sign(data, privateKeyBase64);
    assertThat(Ed25519Service.verify("tampered".getBytes(), sig, publicKeyBase64)).isFalse();
  }

  @Test
  void verify_returnsFalse_forTamperedSignature() {
    byte[] data = "hello".getBytes();
    byte[] sig = Ed25519Service.sign(data, privateKeyBase64);
    sig[0] ^= 0xFF; // flip bits in first byte
    assertThat(Ed25519Service.verify(data, sig, publicKeyBase64)).isFalse();
  }

  @Test
  void verify_returnsFalse_forWrongKeyPair() {
    KeyPair otherKp = SigningKeyGenerator.generate();
    String otherPublic = SigningKeyGenerator.encodePublicKey(otherKp.getPublic());
    byte[] data = "hello".getBytes();
    byte[] sig = Ed25519Service.sign(data, privateKeyBase64);
    assertThat(Ed25519Service.verify(data, sig, otherPublic)).isFalse();
  }

  @Test
  void sign_throwsSigningException_forInvalidBase64() {
    assertThatThrownBy(() -> Ed25519Service.sign("data".getBytes(), "not-valid-base64!!!"))
        .isInstanceOf(SigningException.class)
        .hasMessageContaining("Invalid base64");
  }

  @Test
  void verify_throwsSigningException_forInvalidPublicKeyBase64() {
    byte[] bytes = "x".getBytes();
    byte[] sig = Ed25519Service.sign(bytes, privateKeyBase64);
    assertThatThrownBy(() -> Ed25519Service.verify(bytes, sig, "not-valid-base64!!!"))
        .isInstanceOf(SigningException.class)
        .hasMessageContaining("Invalid base64");
  }

  @Test
  void sign_producesNonEmptySignature() {
    byte[] sig = Ed25519Service.sign("payload".getBytes(), privateKeyBase64);
    assertThat(sig).isNotNull().hasSize(64);
  }

  @Test
  void sign_producesDifferentSignatureForDifferentData() {
    byte[] sig1 = Ed25519Service.sign("aaa".getBytes(), privateKeyBase64);
    byte[] sig2 = Ed25519Service.sign("bbb".getBytes(), privateKeyBase64);
    assertThat(sig1).isNotEqualTo(sig2);
  }
}
