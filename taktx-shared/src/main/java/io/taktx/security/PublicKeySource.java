/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks All rights reserved.
 * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 * Free use is permitted with up to 3 Kafka partitions per topic. See LICENSE file for details.
 * For commercial use or more partitions and features, contact [https://www.taktx.io/contact].
 */
package io.taktx.security;

import java.security.PublicKey;

/**
 * Resolves a RSA public key by JWT issuer string. Implementations are provided by the consuming
 * module (engine, ingester).
 */
@FunctionalInterface
public interface PublicKeySource {
  PublicKey getKey(String issuer) throws AuthorizationTokenException;
}
