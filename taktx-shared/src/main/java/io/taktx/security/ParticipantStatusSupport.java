/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.security;

import io.taktx.dto.ParticipantEffectiveState;
import io.taktx.dto.ParticipantStatusDTO;
import io.taktx.dto.PolicyMismatchReasonDTO;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

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

    return status.toBuilder().mismatchReasons(mismatchReasons).build();
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
    if (normalized.getRole() == null) {
      errors.add("role must not be null");
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

    boolean hasObservedVersion = normalized.getObservedPolicyVersion() != null;
    boolean hasObservedHash = !isBlank(normalized.getObservedPolicyHash());
    if (hasObservedVersion != hasObservedHash) {
      errors.add("observedPolicyVersion and observedPolicyHash must be provided together");
    }
    if (hasObservedVersion && normalized.getObservedPolicyVersion() <= 0) {
      errors.add("observedPolicyVersion must be > 0");
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

  private static boolean isBlank(String value) {
    return value == null || value.isBlank();
  }
}
