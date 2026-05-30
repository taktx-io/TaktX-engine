/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.client;

import io.taktx.dto.ParticipantCapability;
import io.taktx.dto.ParticipantEffectiveState;
import io.taktx.dto.ParticipantStatusDTO;
import io.taktx.security.ParticipantStatusSupport;
import java.util.Map;
import java.util.Set;

/** Aggregated participant counts for simplified operator-facing posture views. */
public record ParticipantSummary(
    int totalParticipants,
    int activationRelevantParticipants,
    int activationRelevantReadyParticipants,
    int activationRelevantBlockingParticipants,
    int protectedRuntimeParticipants,
    int protectedRuntimeAllowedParticipants,
    int protectedRuntimeBlockingParticipants,
    int mismatchedParticipants,
    int staleParticipants,
    int observerOnlyParticipants) {

  static ParticipantSummary from(
      Map<String, ParticipantStatusDTO> participantStatuses,
      Long activePolicyVersion,
      String activePolicyHash,
      long nowMs) {
    int totalParticipants = 0;
    int activationRelevantParticipants = 0;
    int activationRelevantReadyParticipants = 0;
    int activationRelevantBlockingParticipants = 0;
    int protectedRuntimeParticipants = 0;
    int protectedRuntimeAllowedParticipants = 0;
    int protectedRuntimeBlockingParticipants = 0;
    int mismatchedParticipants = 0;
    int staleParticipants = 0;
    int observerOnlyParticipants = 0;

    for (ParticipantStatusDTO status : participantStatuses.values()) {
      if (status == null) {
        continue;
      }
      totalParticipants++;
      Set<ParticipantCapability> capabilities =
          status.getCapabilities() == null ? Set.of() : status.getCapabilities();
      boolean activationRelevant = capabilities.contains(ParticipantCapability.ENFORCER);
      boolean protectedRuntimeParticipant =
          activationRelevant
              || capabilities.contains(ParticipantCapability.PROTECTED_RUNTIME_PARTICIPANT);
      boolean observerOnly = !activationRelevant && !protectedRuntimeParticipant;
      boolean stale =
          status.getEffectiveState() == ParticipantEffectiveState.STALE
              || ParticipantStatusSupport.isExpired(status, nowMs);
      boolean mismatch = status.getEffectiveState() == ParticipantEffectiveState.MISMATCH;
      boolean allowedForProtectedRuntime =
          ParticipantStatusSupport.allowsProtectedDataPlaneParticipation(
              status, activePolicyVersion, activePolicyHash, nowMs);

      if (activationRelevant) {
        activationRelevantParticipants++;
        if (allowedForProtectedRuntime) {
          activationRelevantReadyParticipants++;
        } else {
          activationRelevantBlockingParticipants++;
        }
      }
      if (protectedRuntimeParticipant) {
        protectedRuntimeParticipants++;
        if (allowedForProtectedRuntime) {
          protectedRuntimeAllowedParticipants++;
        } else {
          protectedRuntimeBlockingParticipants++;
        }
      }
      if (mismatch) {
        mismatchedParticipants++;
      }
      if (stale) {
        staleParticipants++;
      }
      if (observerOnly) {
        observerOnlyParticipants++;
      }
    }

    return new ParticipantSummary(
        totalParticipants,
        activationRelevantParticipants,
        activationRelevantReadyParticipants,
        activationRelevantBlockingParticipants,
        protectedRuntimeParticipants,
        protectedRuntimeAllowedParticipants,
        protectedRuntimeBlockingParticipants,
        mismatchedParticipants,
        staleParticipants,
        observerOnlyParticipants);
  }

  public boolean hasActivationRelevantBlockers() {
    return activationRelevantBlockingParticipants > 0;
  }

  public boolean hasProtectedRuntimeBlockers() {
    return protectedRuntimeBlockingParticipants > 0;
  }

  public boolean hasObservedParticipants() {
    return totalParticipants > 0;
  }
}
