/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
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
