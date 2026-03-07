/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks All rights reserved.
 * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 * Free use is permitted with up to 3 Kafka partitions per topic. See LICENSE file for details.
 * For commercial use or more partitions and features, contact [https://www.taktx.io/contact].
 */
package io.taktx.security;

import java.security.*;
import java.util.Base64;

/** Generates Ed25519 key pairs for engine signing. */
public final class SigningKeyGenerator {
  private SigningKeyGenerator() {}

  public static KeyPair generate() {
    try {
      return KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
    } catch (NoSuchAlgorithmException e) {
      throw new SigningException("Ed25519 not available on this JDK", e);
    }
  }

  public static String encodePublicKey(PublicKey key) {
    return Base64.getEncoder().encodeToString(key.getEncoded());
  }

  public static String encodePrivateKey(PrivateKey key) {
    return Base64.getEncoder().encodeToString(key.getEncoded());
  }
}
