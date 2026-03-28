/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.security;

/** Simple mutable identity source used for tests and explicit runtime injection. */
public class StaticSigningIdentitySource implements SigningIdentitySource {

  private volatile SigningIdentity identity;

  public StaticSigningIdentitySource(SigningIdentity identity) {
    this.identity = identity;
  }

  @Override
  public SigningIdentity currentIdentity() {
    return identity;
  }

  public void setIdentity(SigningIdentity identity) {
    this.identity = identity;
  }
}
