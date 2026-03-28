/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.security;

/** Provides private signing keys by key ID. Implementations must be thread-safe. */
public interface SigningKeyProvider {
  String getPrivateKey(String keyId);

  boolean hasKey(String keyId);

  /**
   * Returns the base64-encoded X.509 public key for the given keyId, or {@code null} if not
   * available.
   */
  String getPublicKey(String keyId);

  default String getProviderType() {
    return getClass().getSimpleName();
  }
}
