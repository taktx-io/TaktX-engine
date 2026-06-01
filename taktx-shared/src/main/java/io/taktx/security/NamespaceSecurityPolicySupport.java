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
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

/** Shared normalization, canonicalization, and validation support for namespace security policy. */
public final class NamespaceSecurityPolicySupport {

  private NamespaceSecurityPolicySupport() {}

  /** Returns a normalized copy of the supplied policy. */
  public static NamespaceSecurityPolicyDTO normalize(NamespaceSecurityPolicyDTO policy) {
    if (policy == null) {
      return null;
    }

    return policy.toBuilder()
        .policyHash(firstNonBlank(blankToNull(policy.getPolicyHash()), canonicalHash(policy)))
        .build();
  }

  /** Returns a canonical digest for the authoritative policy content. */
  public static String canonicalHash(NamespaceSecurityPolicyDTO policy) {
    NamespaceSecurityPolicyDTO normalized = policy == null ? NamespaceSecurityPolicyDTO.builder().build() : policy;
    String canonicalForm =
        String.join(
            "\n",
            "mode=" + enumName(normalized.getMode()),
            "policyVersion=" + normalized.getPolicyVersion());

    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      return HexFormat.of()
          .formatHex(digest.digest(canonicalForm.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception e) {
      throw new IllegalStateException("Failed to compute canonical policy hash", e);
    }
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

    NamespaceSecurityPolicyDTO normalized = normalize(policy);

    if (normalized.getMode() == null) {
      errors.add("mode must not be null");
    }
    if (normalized.getPolicyVersion() == null) {
      errors.add("policyVersion must not be null");
    } else if (normalized.getPolicyVersion() <= 0) {
      errors.add("policyVersion must be > 0");
    }

    if (isBlank(normalized.getPolicyHash())) {
      errors.add("policyHash must not be blank after normalization");
    }

    return List.copyOf(errors);
  }

  /** Throws when the policy is invalid. */
  public static NamespaceSecurityPolicyDTO requireValid(NamespaceSecurityPolicyDTO policy) {
    List<String> errors = validationErrors(policy);
    if (!errors.isEmpty()) {
      throw new IllegalArgumentException(String.join("; ", errors));
    }
    return normalize(policy);
  }

  private static String enumName(Enum<?> value) {
    return value != null ? value.name() : "";
  }

  private static String firstNonBlank(String... values) {
    if (values == null) {
      return null;
    }
    for (String value : values) {
      if (!isBlank(value)) {
        return value;
      }
    }
    return null;
  }

  private static boolean isBlank(String value) {
    return value == null || value.isBlank();
  }


  private static String blankToNull(String value) {
    return isBlank(value) ? null : value;
  }
}
