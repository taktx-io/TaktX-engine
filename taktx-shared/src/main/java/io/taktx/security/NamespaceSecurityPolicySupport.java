/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.security;

import io.taktx.dto.NamespaceSecurityPolicyDTO;
import io.taktx.dto.RequiredAuthorizationDTO;
import io.taktx.dto.RequiredSigningDTO;
import io.taktx.dto.SecurityActivationState;
import io.taktx.dto.SecurityMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** Shared normalization, canonicalization, and validation support for namespace security policy. */
public final class NamespaceSecurityPolicySupport {

  private NamespaceSecurityPolicySupport() {}

  /**
   * Returns a normalized copy of the supplied policy.
   *
   * <p>Normalization currently:
   *
   * <ul>
   *   <li>ensures nested requirement DTOs are never {@code null}
   *   <li>copies requested policy aliases between {@code desiredPolicy*} and {@code policy*}
   *   <li>computes a canonical requested policy hash when none is provided
   * </ul>
   */
  public static NamespaceSecurityPolicyDTO normalize(NamespaceSecurityPolicyDTO policy) {
    if (policy == null) {
      return null;
    }

    RequiredSigningDTO requiredSigning =
        policy.getRequiredSigning() != null
            ? policy.getRequiredSigning()
            : RequiredSigningDTO.builder().build();
    RequiredAuthorizationDTO requiredAuthorization =
        policy.getRequiredAuthorization() != null
            ? policy.getRequiredAuthorization()
            : RequiredAuthorizationDTO.builder().build();

    Long desiredPolicyVersion = desiredPolicyVersion(policy);
    String computedCanonicalHash = canonicalHash(policy);
    String desiredPolicyHash = firstNonBlank(policy.getDesiredPolicyHash(), policy.getPolicyHash());
    if (desiredPolicyHash == null) {
      desiredPolicyHash = computedCanonicalHash;
    }

    String policyHash = firstNonBlank(policy.getPolicyHash(), desiredPolicyHash);
    String activePolicyHash = policy.getActivePolicyHash();
    if (policy.getActivationState() == SecurityActivationState.ACTIVE
        && policy.getActivePolicyVersion() != null
        && Objects.equals(policy.getActivePolicyVersion(), desiredPolicyVersion)
        && isBlank(activePolicyHash)) {
      activePolicyHash = desiredPolicyHash;
    }

    return policy.toBuilder()
        .requiredSigning(requiredSigning)
        .requiredAuthorization(requiredAuthorization)
        .breakGlassActor(blankToNull(policy.getBreakGlassActor()))
        .breakGlassReason(blankToNull(policy.getBreakGlassReason()))
        .desiredPolicyVersion(desiredPolicyVersion)
        .desiredPolicyHash(desiredPolicyHash)
        .activePolicyHash(activePolicyHash)
        .policyVersion(desiredPolicyVersion)
        .policyHash(policyHash)
        .build();
  }

  /** Returns a canonical digest for the effective policy content, excluding identity wrappers. */
  public static String canonicalHash(NamespaceSecurityPolicyDTO policy) {
    NamespaceSecurityPolicyDTO normalized =
        policy == null
            ? NamespaceSecurityPolicyDTO.builder().build()
            : policy.toBuilder()
                .requiredSigning(
                    policy.getRequiredSigning() != null
                        ? policy.getRequiredSigning()
                        : RequiredSigningDTO.builder().build())
                .requiredAuthorization(
                    policy.getRequiredAuthorization() != null
                        ? policy.getRequiredAuthorization()
                        : RequiredAuthorizationDTO.builder().build())
                .build();

    String canonicalForm =
        String.join(
            "\n",
            "mode=" + enumName(normalized.getMode()),
            "requiredSigning.engineOutbound=" + normalized.getRequiredSigning().isEngineOutbound(),
            "requiredSigning.clientCommands=" + normalized.getRequiredSigning().isClientCommands(),
            "requiredSigning.workerResponses="
                + normalized.getRequiredSigning().isWorkerResponses(),
            "requiredAuthorization.startCommands="
                + normalized.getRequiredAuthorization().isStartCommands(),
            "requiredAuthorization.externalTaskCompletion="
                + normalized.getRequiredAuthorization().isExternalTaskCompletion(),
            "requiredAuthorization.userTaskCompletion="
                + normalized.getRequiredAuthorization().isUserTaskCompletion(),
            "trustAnchorRequired=" + normalized.isTrustAnchorRequired());

    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      return HexFormat.of()
          .formatHex(digest.digest(canonicalForm.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception e) {
      throw new IllegalStateException("Failed to compute canonical policy hash", e);
    }
  }

  public static Long desiredPolicyVersion(NamespaceSecurityPolicyDTO policy) {
    if (policy == null) {
      return null;
    }
    return policy.getDesiredPolicyVersion() != null
        ? policy.getDesiredPolicyVersion()
        : policy.getPolicyVersion();
  }

  public static String desiredPolicyHash(NamespaceSecurityPolicyDTO policy) {
    if (policy == null) {
      return null;
    }
    return firstNonBlank(policy.getDesiredPolicyHash(), policy.getPolicyHash());
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

  /** Parses role-relevant signing requirement names from a comma-separated string. */
  public static RequiredSigningDTO parseRequiredSigning(String rawValue) {
    return parseRequiredSigning(splitRequirementTokens(rawValue));
  }

  /** Parses role-relevant signing requirement names from a token set. */
  public static RequiredSigningDTO parseRequiredSigning(Set<String> rawTokens) {
    Set<String> tokens = normalizeRequirementTokens(rawTokens);
    validateRequirementTokens(tokens, Set.of("ENGINE_OUTBOUND", "CLIENT_COMMANDS", "WORKER_RESPONSES"), "signing requirement");
    return RequiredSigningDTO.builder()
        .engineOutbound(tokens.contains("ENGINE_OUTBOUND"))
        .clientCommands(tokens.contains("CLIENT_COMMANDS"))
        .workerResponses(tokens.contains("WORKER_RESPONSES"))
        .build();
  }

  /** Parses role-relevant authorization requirement names from a comma-separated string. */
  public static RequiredAuthorizationDTO parseRequiredAuthorization(String rawValue) {
    return parseRequiredAuthorization(splitRequirementTokens(rawValue));
  }

  /** Parses role-relevant authorization requirement names from a token set. */
  public static RequiredAuthorizationDTO parseRequiredAuthorization(Set<String> rawTokens) {
    Set<String> tokens = normalizeRequirementTokens(rawTokens);
    validateRequirementTokens(
        tokens,
        Set.of("START_COMMANDS", "EXTERNAL_TASK_COMPLETION", "USER_TASK_COMPLETION"),
        "authorization requirement");
    return RequiredAuthorizationDTO.builder()
        .startCommands(tokens.contains("START_COMMANDS"))
        .externalTaskCompletion(tokens.contains("EXTERNAL_TASK_COMPLETION"))
        .userTaskCompletion(tokens.contains("USER_TASK_COMPLETION"))
        .build();
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
    if (normalized.getActivationState() == null) {
      errors.add("activationState must not be null");
    }

    Long desiredPolicyVersion = normalized.getDesiredPolicyVersion();
    if (desiredPolicyVersion == null) {
      errors.add("desiredPolicyVersion (or policyVersion) must not be null");
    } else if (desiredPolicyVersion <= 0) {
      errors.add("desiredPolicyVersion must be > 0");
    }

    if (policy.getDesiredPolicyVersion() != null
        && policy.getPolicyVersion() != null
        && !Objects.equals(policy.getDesiredPolicyVersion(), policy.getPolicyVersion())) {
      errors.add("policyVersion must match desiredPolicyVersion when both are provided");
    }

    if (isBlank(normalized.getDesiredPolicyHash())) {
      errors.add("desiredPolicyHash must not be blank after normalization");
    }

    if (!isBlank(policy.getDesiredPolicyHash())
        && !isBlank(policy.getPolicyHash())
        && !Objects.equals(policy.getDesiredPolicyHash(), policy.getPolicyHash())) {
      errors.add("policyHash must match desiredPolicyHash when both are provided");
    }

    if (normalized.getMode() == SecurityMode.ANCHORED_SECURED
        && !normalized.isTrustAnchorRequired()) {
      errors.add("ANCHORED_SECURED requires trustAnchorRequired=true");
    }

    boolean hasBreakGlassActor = !isBlank(normalized.getBreakGlassActor());
    boolean hasBreakGlassReason = !isBlank(normalized.getBreakGlassReason());
    if (hasBreakGlassActor != hasBreakGlassReason) {
      errors.add("breakGlassActor and breakGlassReason must be provided together");
    }

    boolean hasActiveVersion = normalized.getActivePolicyVersion() != null;
    boolean hasActiveHash = !isBlank(normalized.getActivePolicyHash());
    if (hasActiveVersion != hasActiveHash) {
      errors.add("activePolicyVersion and activePolicyHash must be provided together");
    }
    if (hasActiveVersion && normalized.getActivePolicyVersion() <= 0) {
      errors.add("activePolicyVersion must be > 0");
    }

    if (normalized.getActivationState() == SecurityActivationState.ACTIVE) {
      if (!hasActiveVersion || !hasActiveHash) {
        errors.add("ACTIVE policy requires activePolicyVersion and activePolicyHash");
      }
      if (!Objects.equals(
          normalized.getDesiredPolicyVersion(), normalized.getActivePolicyVersion())) {
        errors.add("ACTIVE policy requires desiredPolicyVersion to match activePolicyVersion");
      }
      if (!Objects.equals(normalized.getDesiredPolicyHash(), normalized.getActivePolicyHash())) {
        errors.add("ACTIVE policy requires desiredPolicyHash to match activePolicyHash");
      }
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

  private static Set<String> splitRequirementTokens(String rawValue) {
    if (isBlank(rawValue)) {
      return Set.of();
    }
    return Stream.of(rawValue.split(","))
        .map(String::trim)
        .filter(token -> !token.isBlank())
        .collect(Collectors.toUnmodifiableSet());
  }

  private static Set<String> normalizeRequirementTokens(Set<String> rawTokens) {
    if (rawTokens == null || rawTokens.isEmpty()) {
      return Set.of();
    }
    return rawTokens.stream()
        .filter(Objects::nonNull)
        .map(String::trim)
        .filter(token -> !token.isBlank())
        .map(token -> token.replace('-', '_').replace(' ', '_').toUpperCase())
        .collect(Collectors.toUnmodifiableSet());
  }

  private static void validateRequirementTokens(
      Set<String> actual, Set<String> supported, String label) {
    for (String token : actual) {
      if (!supported.contains(token)) {
        throw new IllegalArgumentException("Unsupported " + label + ": " + token);
      }
    }
  }

  private static String blankToNull(String value) {
    return isBlank(value) ? null : value;
  }
}
