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
 * Default trust policy for standalone and docker-compose deployments.
 *
 * <p>Trusts any non-revoked key whose declared (or effective) role is sufficient for the required
 * role, using the hierarchy: {@code PLATFORM ⊇ ENGINE ⊇ CLIENT}.
 *
 * <p>This is an honor-system policy: the declared role is taken at face value. Enforcement against
 * malicious publishers requires Kafka ACLs (infrastructure) or the future {@code
 * AnchoredKeyTrustPolicy} (countersigning).
 */
public class OpenKeyTrustPolicy implements KeyTrustPolicy {

  @Override
  public boolean isTrustedForRole(SigningKeyDTO key, KeyRole requiredRole) {
    if (key == null) return false;
    if (key.getStatus() == SigningKeyDTO.KeyStatus.REVOKED) return false;

    KeyRole effectiveRole = key.effectiveRole(); // null → CLIENT

    return roleLevel(effectiveRole) >= roleLevel(requiredRole);
  }

  private static int roleLevel(KeyRole role) {
    return switch (role) {
      case CLIENT -> 0;
      case ENGINE -> 1;
      case PLATFORM -> 2;
    };
  }
}
