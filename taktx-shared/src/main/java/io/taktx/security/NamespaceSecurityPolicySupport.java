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
import java.util.ArrayList;
import java.util.List;

/** Shared normalization and validation support for namespace security policy. */
public final class NamespaceSecurityPolicySupport {

  private NamespaceSecurityPolicySupport() {}

  /** Returns a normalized copy of the supplied policy (no-op for mode-only DTO). */
  public static NamespaceSecurityPolicyDTO normalize(NamespaceSecurityPolicyDTO policy) {
    return policy;
  }

  /** Parses operator-facing security mode text with common dash/underscore/case variants. */
  public static SecurityMode parseSecurityMode(String rawValue) {
    if (isBlank(rawValue)) {
      return null;
    }
    String normalized = rawValue.trim().replace('-', '_').replace(' ', '_').toUpperCase();
    try {
      return SecurityMode.valueOf(normalized);
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException("Unsupported security mode: " + rawValue, e);
    }
  }

  /** Returns validation errors without throwing. */
  public static List<String> validationErrors(NamespaceSecurityPolicyDTO policy) {
    List<String> errors = new ArrayList<>();
    if (policy == null) {
      errors.add("policy must not be null");
      return errors;
    }
    if (policy.getMode() == null) {
      errors.add("mode must not be null");
    }
    return List.copyOf(errors);
  }

  /** Throws when the policy is invalid. */
  public static NamespaceSecurityPolicyDTO requireValid(NamespaceSecurityPolicyDTO policy) {
    List<String> errors = validationErrors(policy);
    if (!errors.isEmpty()) {
      throw new IllegalArgumentException(String.join("; ", errors));
    }
    return policy;
  }

  private static boolean isBlank(String value) {
    return value == null || value.isBlank();
  }
}
