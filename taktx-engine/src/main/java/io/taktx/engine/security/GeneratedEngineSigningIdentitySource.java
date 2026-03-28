/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
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
