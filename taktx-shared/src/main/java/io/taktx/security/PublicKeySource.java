/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.security;

import java.security.PublicKey;

/**
 * Resolves an RSA public key by JWT {@code kid} header. Implementations are provided by the
 * consuming module (engine, ingester).
 */
@FunctionalInterface
public interface PublicKeySource {
  PublicKey getKey(String kid) throws AuthorizationTokenException;
}
