/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.security;

import io.taktx.dto.ParticipantCapability;
import io.taktx.dto.ParticipantEffectiveState;
import io.taktx.dto.ParticipantStatusDTO;
import io.taktx.dto.PolicyMismatchReasonDTO;
import io.taktx.dto.SecurityMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Shared normalization and validation support for participant status telemetry. */
public final class ParticipantStatusSupport {

  private ParticipantStatusSupport() {}

  public static ParticipantStatusDTO normalize(ParticipantStatusDTO status) {
    if (status == null) {
      return null;
    }

    List<PolicyMismatchReasonDTO> mismatchReasons =
        status.getMismatchReasons() == null
            ? List.of()
            : status.getMismatchReasons().stream()
                .filter(Objects::nonNull)
                .map(
                    reason ->
                        reason.toBuilder()
                            .metadata(
                                reason.getMetadata() == null
                                    ? Map.of()
                                    : Map.copyOf(reason.getMetadata()))
                            .build())
                .toList();

    Set<ParticipantCapability> capabilities = normalizeSet(status.getCapabilities());
    Set<SecurityMode> supportedModes =
        normalizeSupportedModes(status.getSupportedModes(), capabilities);
    String componentType = normalizeOptionalString(status.getComponentType());

    return status.toBuilder()
        .componentType(componentType)
        .capabilities(capabilities)
        .supportedModes(supportedModes)
        .mismatchReasons(mismatchReasons)
        .build();
  }

  public static List<String> validationErrors(ParticipantStatusDTO status) {
    List<String> errors = new ArrayList<>();
    if (status == null) {
      errors.add("status must not be null");
      return errors;
    }

    ParticipantStatusDTO normalized = normalize(status);

    if (isBlank(normalized.getParticipantId())) {
      errors.add("participantId must not be blank");
    }
    if (isBlank(normalized.getParticipantInstanceId())) {
      errors.add("participantInstanceId must not be blank");
    }
    if (normalized.getParticipantKind() == null) {
      errors.add("participantKind must not be null");
    }
    if (containsNull(normalized.getCapabilities())) {
      errors.add("capabilities must not contain null values");
    }
    if (normalized.getCapabilities().isEmpty()) {
      errors.add("capabilities must not be empty");
    }
    if (containsNull(normalized.getSupportedModes())) {
      errors.add("supportedModes must not contain null values");
    }
    if (!normalized.getSupportedModes().contains(SecurityMode.OPEN)) {
      errors.add("supportedModes must include OPEN");
    }
    if (isBlank(normalized.getNamespace())) {
      errors.add("namespace must not be blank");
    }
    if (normalized.getStartedAt() <= 0) {
      errors.add("startedAt must be > 0");
    }
    if (normalized.getLastSeenAt() <= 0) {
      errors.add("lastSeenAt must be > 0");
    }
    if (normalized.getStatusExpiresAt() <= 0) {
      errors.add("statusExpiresAt must be > 0");
    }
    if (normalized.getLastSeenAt() < normalized.getStartedAt()) {
      errors.add("lastSeenAt must be >= startedAt");
    }
    if (normalized.getStatusExpiresAt() < normalized.getLastSeenAt()) {
      errors.add("statusExpiresAt must be >= lastSeenAt");
    }
    if (normalized.getStatusVerificationLevel() == null) {
      errors.add("statusVerificationLevel must not be null");
    }
    if (normalized.getEffectiveState() == null) {
      errors.add("effectiveState must not be null");
    }
    if (normalized.isReadyForDataPlane()
        && normalized.getEffectiveState() != ParticipantEffectiveState.READY) {
      errors.add("readyForDataPlane=true requires effectiveState=READY");
    }

    for (PolicyMismatchReasonDTO reason : normalized.getMismatchReasons()) {
      if (isBlank(reason.getCode())) {
        errors.add("mismatchReasons[].code must not be blank");
      }
      if (isBlank(reason.getMessage())) {
        errors.add("mismatchReasons[].message must not be blank");
      }
    }

    return List.copyOf(errors);
  }

  public static ParticipantStatusDTO requireValid(ParticipantStatusDTO status) {
    List<String> errors = validationErrors(status);
    if (!errors.isEmpty()) {
      throw new IllegalArgumentException(String.join("; ", errors));
    }
    return normalize(status);
  }

  public static boolean isExpired(ParticipantStatusDTO status, long nowMs) {
    return status == null || status.getStatusExpiresAt() <= nowMs;
  }

  /**
   * Returns whether a participant may take part in protected data-plane behavior.
   *
   * <p>Based on non-expired status, {@code READY} effective state, and the participant's explicit
   * {@code readyForDataPlane} flag. Mode version/hash matching was removed in 0.8.0 — mode is now
   * startup-static and cannot differ between participants.
   */
  public static boolean allowsProtectedDataPlaneParticipation(
      ParticipantStatusDTO status, long nowMs) {
    if (isExpired(status, nowMs)) {
      return false;
    }
    ParticipantStatusDTO normalized = normalize(status);
    return normalized.getEffectiveState() == ParticipantEffectiveState.READY
        && normalized.isReadyForDataPlane();
  }

  /** Returns the explicit support-in-principle modes for the participant. */
  public static Set<SecurityMode> supportedModes(ParticipantStatusDTO status) {
    if (status == null) {
      return Set.of();
    }
    return normalize(status).getSupportedModes();
  }

  /** Returns whether the participant can support the supplied security mode in principle. */
  public static boolean supportsMode(ParticipantStatusDTO status, SecurityMode mode) {
    return mode != null && supportedModes(status).contains(mode);
  }

  /** Returns whether the participant can participate in protected runtime traffic in principle. */
  public static boolean supportsProtectedRuntimeParticipation(ParticipantStatusDTO status) {
    if (status == null || status.getCapabilities() == null) {
      return false;
    }
    return status.getCapabilities().contains(ParticipantCapability.ENFORCER)
        || status.getCapabilities().contains(ParticipantCapability.PROTECTED_RUNTIME_PARTICIPANT);
  }

  /** Returns whether the participant can validate anchored trust requirements in principle. */
  public static boolean supportsTrustAnchorValidation(ParticipantStatusDTO status) {
    return supportsMode(status, SecurityMode.ANCHORED);
  }

  /** Derives support-in-principle modes from coarse participant capabilities. */
  public static Set<SecurityMode> supportedModesForCapabilities(
      Set<ParticipantCapability> capabilities) {
    EnumSet<SecurityMode> supportedModes = EnumSet.of(SecurityMode.OPEN);
    if (capabilities == null || capabilities.isEmpty()) {
      return Set.copyOf(supportedModes);
    }
    if (capabilities.contains(ParticipantCapability.ENFORCER)
        || capabilities.contains(ParticipantCapability.PROTECTED_RUNTIME_PARTICIPANT)) {
      supportedModes.add(SecurityMode.ANCHORED);
    }
    return Set.copyOf(supportedModes);
  }

  private static boolean isBlank(String value) {
    return value == null || value.isBlank();
  }

  private static String normalizeOptionalString(String value) {
    return isBlank(value) ? null : value.trim();
  }

  private static <T> Set<T> normalizeSet(Set<T> values) {
    if (values == null || values.isEmpty()) {
      return Set.of();
    }
    return Collections.unmodifiableSet(new LinkedHashSet<>(values));
  }

  private static Set<SecurityMode> normalizeSupportedModes(
      Set<SecurityMode> supportedModes, Set<ParticipantCapability> capabilities) {
    if (supportedModes == null || supportedModes.isEmpty()) {
      return supportedModesForCapabilities(capabilities);
    }
    return Collections.unmodifiableSet(new LinkedHashSet<>(supportedModes));
  }

  private static boolean containsNull(Set<?> values) {
    return values != null && values.stream().anyMatch(Objects::isNull);
  }
}
