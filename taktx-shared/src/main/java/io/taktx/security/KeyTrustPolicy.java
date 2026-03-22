/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks All rights reserved.
 * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 * Free use is permitted with up to 3 Kafka partitions per topic. See LICENSE file for details.
 * For commercial use or more partitions and features, contact [https://www.taktx.io/contact].
 */
package io.taktx.security;

import io.taktx.dto.KeyRole;
import io.taktx.dto.SigningKeyDTO;

/**
 * Determines whether a signing key is trusted to perform operations requiring a given role.
 *
 * <p>Implementations:
 *
 * <ul>
 *   <li>{@link OpenKeyTrustPolicy} — trusts any non-revoked key for its declared role
 *       (standalone/dev)
 *   <li>Future: {@code AnchoredKeyTrustPolicy} — additionally verifies a countersignature from a
 *       platform root key
 * </ul>
 */
public interface KeyTrustPolicy {

  /**
   * Returns {@code true} if the given signing key is trusted to perform operations that require the
   * specified role.
   *
   * @param key the signing key entry from the KTable (may be {@code null})
   * @param requiredRole the minimum role needed for the operation
   */
  boolean isTrustedForRole(SigningKeyDTO key, KeyRole requiredRole);
}
