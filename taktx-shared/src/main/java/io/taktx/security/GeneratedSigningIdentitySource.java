/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.security;

import java.security.KeyPair;
import java.util.UUID;

/** Identity source that generates a single Ed25519 keypair once and then keeps it stable. */
public class GeneratedSigningIdentitySource implements SigningIdentitySource {

  private final SigningIdentity identity;

  public GeneratedSigningIdentitySource(String keyIdPrefix) {
    this.identity = generate(keyIdPrefix);
  }

  public GeneratedSigningIdentitySource(SigningIdentity identity) {
    this.identity = identity;
  }

  @Override
  public SigningIdentity currentIdentity() {
    return identity;
  }

  private static SigningIdentity generate(String keyIdPrefix) {
    String prefix = keyIdPrefix != null && !keyIdPrefix.isBlank() ? keyIdPrefix : "generated-";
    KeyPair keyPair = SigningKeyGenerator.generate();
    return SigningIdentity.ed25519(
        prefix + UUID.randomUUID(),
        SigningKeyGenerator.encodePrivateKey(keyPair.getPrivate()),
        SigningKeyGenerator.encodePublicKey(keyPair.getPublic()));
  }
}
