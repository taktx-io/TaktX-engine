/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.client;

import io.taktx.dto.ParticipantStatusDTO;
import io.taktx.dto.SecurityActivationState;
import io.taktx.dto.SecurityEventDTO;
import io.taktx.dto.SecurityMode;
import jakarta.annotation.Nullable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Console-grade namespace security posture snapshot assembled from public policy,
 * participant-status, and security-event topics only.
 */
public record SecurityPostureSnapshot(
    ObservedPolicySnapshot observedPolicy,
    @Nullable SecurityMode effectiveMode,
    @Nullable Long effectivePolicyVersion,
    @Nullable String effectivePolicyHash,
    Map<String, ParticipantStatusDTO> participantStatuses,
    List<ParticipantPostureMismatch> mismatchReasons,
    List<SecurityEventDTO> recentSecurityEvents) {

  public SecurityPostureSnapshot {
    observedPolicy = observedPolicy != null ? observedPolicy : ObservedPolicySnapshot.empty();
    participantStatuses =
        participantStatuses == null
            ? Map.of()
            : Map.copyOf(new LinkedHashMap<>(participantStatuses));
    mismatchReasons = mismatchReasons == null ? List.of() : List.copyOf(mismatchReasons);
    recentSecurityEvents =
        recentSecurityEvents == null ? List.of() : List.copyOf(recentSecurityEvents);
  }

  public static SecurityPostureSnapshot empty() {
    return from(ObservedPolicySnapshot.empty(), Map.of(), List.of());
  }

  public static SecurityPostureSnapshot from(
      ObservedPolicySnapshot observedPolicy,
      Map<String, ParticipantStatusDTO> participantStatuses,
      List<SecurityEventDTO> recentSecurityEvents) {
    ObservedPolicySnapshot effectiveObservedPolicy =
        observedPolicy != null ? observedPolicy : ObservedPolicySnapshot.empty();
    Map<String, ParticipantStatusDTO> effectiveParticipantStatuses =
        participantStatuses == null
            ? Map.of()
            : Map.copyOf(new LinkedHashMap<>(participantStatuses));
    List<SecurityEventDTO> effectiveRecentSecurityEvents =
        recentSecurityEvents == null ? List.of() : List.copyOf(recentSecurityEvents);
    return new SecurityPostureSnapshot(
        effectiveObservedPolicy,
        effectiveObservedPolicy.effectiveMode(),
        effectiveObservedPolicy.effectivePolicyVersion(),
        effectiveObservedPolicy.effectivePolicyHash(),
        effectiveParticipantStatuses,
        flattenMismatches(effectiveParticipantStatuses),
        effectiveRecentSecurityEvents);
  }

  public @Nullable SecurityActivationState currentActivationState() {
    return observedPolicy.currentActivationState();
  }

  public boolean hasEffectivePolicy() {
    return effectiveMode != null || effectivePolicyVersion != null || effectivePolicyHash != null;
  }

  public boolean hasParticipantStatuses() {
    return !participantStatuses.isEmpty();
  }

  public boolean hasMismatchReasons() {
    return !mismatchReasons.isEmpty();
  }

  public boolean hasRecentSecurityEvents() {
    return !recentSecurityEvents.isEmpty();
  }

  public @Nullable ParticipantStatusDTO participantStatus(String participantInstanceId) {
    return participantStatuses.get(participantInstanceId);
  }

  public List<ParticipantStatusDTO> participantsWithMismatches() {
    return participantStatuses.values().stream()
        .filter(
            status -> status.getMismatchReasons() != null && !status.getMismatchReasons().isEmpty())
        .toList();
  }

  private static List<ParticipantPostureMismatch> flattenMismatches(
      Map<String, ParticipantStatusDTO> participantStatuses) {
    List<ParticipantPostureMismatch> mismatches = new ArrayList<>();
    participantStatuses.forEach(
        (participantInstanceId, status) -> {
          if (status == null || status.getMismatchReasons() == null) {
            return;
          }
          status
              .getMismatchReasons()
              .forEach(
                  mismatchReason ->
                      mismatches.add(
                          new ParticipantPostureMismatch(
                              participantInstanceId,
                              status.getParticipantId(),
                              status.getParticipantKind(),
                              status.getComponentType(),
                              mismatchReason)));
        });
    return List.copyOf(mismatches);
  }
}
