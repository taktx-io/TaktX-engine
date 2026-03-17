/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks All rights reserved.
 * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 * Free use is permitted with up to 3 Kafka partitions per topic. See LICENSE file for details.
 * For commercial use or more partitions and features, contact [https://www.taktx.io/contact].
 */
package io.taktx.engine.security;

import io.taktx.security.GeneratedSigningIdentitySource;
import io.taktx.security.SigningIdentity;
import io.taktx.security.SigningIdentitySource;

/** Default engine signing-identity source that generates a fresh Ed25519 identity on startup. */
public class GeneratedEngineSigningIdentitySource implements SigningIdentitySource {

  private final GeneratedSigningIdentitySource delegate =
      new GeneratedSigningIdentitySource("engine-");

  @Override
  public SigningIdentity currentIdentity() {
    return delegate.currentIdentity();
  }
}
