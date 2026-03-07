/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks All rights reserved.
 */
package io.taktx.security;

/** Provides private signing keys by key ID. Implementations must be thread-safe. */
public interface SigningKeyProvider {
  String getPrivateKey(String keyId);

  boolean hasKey(String keyId);

  default String getProviderType() {
    return getClass().getSimpleName();
  }
}
