/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
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
