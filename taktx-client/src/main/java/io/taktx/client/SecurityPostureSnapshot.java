/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.client;

import io.taktx.dto.ParticipantStatusDTO;
import io.taktx.dto.SecurityEventDTO;
import io.taktx.dto.SecurityMode;
import io.taktx.security.ParticipantStatusSupport;
import jakarta.annotation.Nullable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Console-grade namespace security posture snapshot assembled from participant-status and
 * security-event topics. The effective mode is startup-static and sourced directly from {@code
 * taktx-security-policy} (mode-only) or from participant self-reports.
 */
public record SecurityPostureSnapshot(
    @Nullable SecurityMode effectiveMode,
    Map<String, ParticipantStatusDTO> participantStatuses,
    List<ParticipantPostureMismatch> mismatchReasons,
    List<SecurityEventDTO> recentSecurityEvents) {

  public SecurityPostureSnapshot {
    participantStatuses =
        participantStatuses == null
            ? Map.of()
            : Map.copyOf(new LinkedHashMap<>(participantStatuses));
    mismatchReasons = mismatchReasons == null ? List.of() : List.copyOf(mismatchReasons);
    recentSecurityEvents =
        recentSecurityEvents == null ? List.of() : List.copyOf(recentSecurityEvents);
  }

  public static SecurityPostureSnapshot empty() {
    return new SecurityPostureSnapshot(null, Map.of(), List.of(), List.of());
  }

  public static SecurityPostureSnapshot from(
      Map<String, ParticipantStatusDTO> participantStatuses,
      List<SecurityEventDTO> recentSecurityEvents) {
    Map<String, ParticipantStatusDTO> statuses =
        participantStatuses == null
            ? Map.of()
            : Map.copyOf(new LinkedHashMap<>(participantStatuses));
    List<SecurityEventDTO> events =
        recentSecurityEvents == null ? List.of() : List.copyOf(recentSecurityEvents);
    // Derive effective mode from any engine participant that self-reports it
    SecurityMode derivedMode = deriveEffectiveMode(statuses);
    return new SecurityPostureSnapshot(derivedMode, statuses, flattenMismatches(statuses), events);
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
        .filter(s -> s.getMismatchReasons() != null && !s.getMismatchReasons().isEmpty())
        .toList();
  }

  private static @Nullable SecurityMode deriveEffectiveMode(
      Map<String, ParticipantStatusDTO> statuses) {
    // Mode is startup-static. Only the engine/enforcer may authoritatively self-report that the
    // namespace is running in ANCHORED mode; protected-runtime clients merely advertise support in
    // principle and must not flip the derived namespace posture on their own.
    return statuses.values().stream()
        .filter(
            s ->
                s != null
                    && (s.getParticipantKind() == io.taktx.dto.ParticipantKind.ENGINE
                        || (s.getCapabilities() != null
                            && s.getCapabilities()
                                .contains(io.taktx.dto.ParticipantCapability.ENFORCER)))
                    && ParticipantStatusSupport.supportsMode(s, SecurityMode.ANCHORED))
        .findFirst()
        .map(s -> SecurityMode.ANCHORED)
        .orElse(null);
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
                  reason ->
                      mismatches.add(
                          new ParticipantPostureMismatch(
                              participantInstanceId,
                              status.getParticipantId(),
                              status.getParticipantKind(),
                              status.getComponentType(),
                              reason)));
        });
    return List.copyOf(mismatches);
  }
}
