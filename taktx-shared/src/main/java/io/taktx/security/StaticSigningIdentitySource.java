/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks All rights reserved.
 * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 * Free use is permitted with up to 3 Kafka partitions per topic. See LICENSE file for details.
 * For commercial use or more partitions and features, contact [https://www.taktx.io/contact].
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
