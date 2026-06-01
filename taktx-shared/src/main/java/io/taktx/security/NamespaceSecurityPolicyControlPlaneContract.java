/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.security;

import io.taktx.dto.NamespaceSecurityPolicyDTO;
import io.taktx.dto.SecurityMode;
import java.util.EnumSet;
import java.util.Set;

/** Shared authoritative mutation contract for namespace security policy control-plane records. */
public final class NamespaceSecurityPolicyControlPlaneContract {

  public static final String POLICY_RECORD_KEY = "policy";

  private NamespaceSecurityPolicyControlPlaneContract() {}

  /**
   * Returns the fixed compacted-topic record key for the authoritative namespace security policy.
   */
  public static String policyRecordKey() {
    return POLICY_RECORD_KEY;
  }

  /** Returns the required security properties for authoritative namespace policy mutation. */
  public static Set<AuthoritativeControlPlaneSecurityProperty> requiredWriterSecurityProperties() {
    return Set.copyOf(
        EnumSet.of(
            AuthoritativeControlPlaneSecurityProperty.BROKER_AUTHORIZATION_REQUIRED,
            AuthoritativeControlPlaneSecurityProperty.TRUSTED_WRITER_PATH_ONLY,
            AuthoritativeControlPlaneSecurityProperty.FIXED_RECORD_KEY_REQUIRED));
  }

  /** Returns the required security properties for a specific policy mutation. */
  public static Set<AuthoritativeControlPlaneSecurityProperty> requiredWriterSecurityProperties(
      NamespaceSecurityPolicyDTO policy) {
    EnumSet<AuthoritativeControlPlaneSecurityProperty> requirements =
        EnumSet.copyOf(requiredWriterSecurityProperties());
    if (policy == null || policy.getMode() == null) {
      return Set.copyOf(requirements);
    }
    if (policy.getMode() == SecurityMode.ANCHORED) {
      requirements.add(
          AuthoritativeControlPlaneSecurityProperty.INTEGRITY_PROTECTION_REQUIRED_IN_SECURED_MODES);
    }
    return Set.copyOf(requirements);
  }
}
