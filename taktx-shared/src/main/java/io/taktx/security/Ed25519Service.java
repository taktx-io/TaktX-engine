/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.security;

import java.security.*;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/** Ed25519 sign/verify using JDK built-in support (Java 15+). Thread-safe. */
public final class Ed25519Service {

  private static final String ALGORITHM = "Ed25519";

  private Ed25519Service() {}

  /**
   * Signs data with an Ed25519 private key.
   *
   * @param data the bytes to sign (typically CBOR-serialised payload)
   * @param privateKeyBase64 base64-encoded PKCS8 private key
   * @return 64-byte Ed25519 signature
   */
  public static byte[] sign(byte[] data, String privateKeyBase64) {
    try {
      byte[] keyBytes = Base64.getDecoder().decode(privateKeyBase64);
      PrivateKey pk =
          KeyFactory.getInstance(ALGORITHM).generatePrivate(new PKCS8EncodedKeySpec(keyBytes));
      Signature sig = Signature.getInstance(ALGORITHM);
      sig.initSign(pk);
      sig.update(data);
      return sig.sign();
    } catch (IllegalArgumentException e) {
      throw new SigningException("Invalid base64 in private key", e);
    } catch (GeneralSecurityException e) {
      throw new SigningException("Failed to sign: " + e.getMessage(), e);
    }
  }

  /**
   * Verifies an Ed25519 signature.
   *
   * @param data the original bytes that were signed
   * @param signatureBytes the 64-byte signature to verify
   * @param publicKeyBase64 base64-encoded X.509 public key
   * @return {@code true} if the signature is valid
   */
  public static boolean verify(byte[] data, byte[] signatureBytes, String publicKeyBase64) {
    try {
      byte[] keyBytes = Base64.getDecoder().decode(publicKeyBase64);
      PublicKey pk =
          KeyFactory.getInstance(ALGORITHM).generatePublic(new X509EncodedKeySpec(keyBytes));
      Signature sig = Signature.getInstance(ALGORITHM);
      sig.initVerify(pk);
      sig.update(data);
      return sig.verify(signatureBytes);
    } catch (IllegalArgumentException e) {
      throw new SigningException("Invalid base64 in public key", e);
    } catch (SignatureException e) {
      return false; // invalid signature bytes — treat as verification failure
    } catch (GeneralSecurityException e) {
      throw new SigningException("Failed to verify: " + e.getMessage(), e);
    }
  }
}
