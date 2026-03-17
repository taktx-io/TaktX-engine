/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks All rights reserved.
 * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 * Free use is permitted with up to 3 Kafka partitions per topic. See LICENSE file for details.
 * For commercial use or more partitions and features, contact [https://www.taktx.io/contact].
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
