/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.security;

import java.util.Base64;
import java.util.Objects;

/** Immutable signing identity consisting of a key ID plus signing key material. */
public final class SigningIdentity {

  private static final String ED25519 = "Ed25519";

  private final String keyId;
  private final String privateKeyBase64;
  private final String publicKeyBase64;
  private final String algorithm;

  private SigningIdentity(
      String keyId, String privateKeyBase64, String publicKeyBase64, String algorithm) {
    if (keyId == null || keyId.isBlank()) {
      throw new IllegalArgumentException("keyId must not be blank");
    }
    if (privateKeyBase64 == null || privateKeyBase64.isBlank()) {
      throw new IllegalArgumentException("privateKeyBase64 must not be blank");
    }
    if (algorithm == null || algorithm.isBlank()) {
      throw new IllegalArgumentException("algorithm must not be blank");
    }
    this.keyId = keyId;
    this.privateKeyBase64 = privateKeyBase64;
    this.publicKeyBase64 = publicKeyBase64;
    this.algorithm = algorithm;
  }

  public static SigningIdentity ed25519(
      String keyId, String privateKeyBase64, String publicKeyBase64) {
    return new SigningIdentity(keyId, privateKeyBase64, publicKeyBase64, ED25519);
  }

  public static SigningIdentity of(
      String keyId, String privateKeyBase64, String publicKeyBase64, String algorithm) {
    return new SigningIdentity(keyId, privateKeyBase64, publicKeyBase64, algorithm);
  }

  public String getKeyId() {
    return keyId;
  }

  public String getPrivateKeyBase64() {
    return privateKeyBase64;
  }

  public String getPublicKeyBase64() {
    return publicKeyBase64;
  }

  public String getAlgorithm() {
    return algorithm;
  }

  public boolean hasPublicKey() {
    return publicKeyBase64 != null && !publicKeyBase64.isBlank();
  }

  public String toHeaderValue(byte[] signatureBytes) {
    return keyId + "." + Base64.getEncoder().encodeToString(signatureBytes);
  }

  @Override
  public boolean equals(Object other) {
    if (this == other) {
      return true;
    }
    if (!(other instanceof SigningIdentity that)) {
      return false;
    }
    return Objects.equals(keyId, that.keyId)
        && Objects.equals(privateKeyBase64, that.privateKeyBase64)
        && Objects.equals(publicKeyBase64, that.publicKeyBase64)
        && Objects.equals(algorithm, that.algorithm);
  }

  @Override
  public int hashCode() {
    return Objects.hash(keyId, privateKeyBase64, publicKeyBase64, algorithm);
  }

  @Override
  public String toString() {
    return "SigningIdentity{"
        + "keyId='"
        + keyId
        + '\''
        + ", hasPublicKey="
        + hasPublicKey()
        + ", algorithm='"
        + algorithm
        + '\''
        + '}';
  }
}
