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
