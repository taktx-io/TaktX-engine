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
import io.taktx.dto.SecurityActivationState;
import io.taktx.dto.SecurityEventDTO;
import io.taktx.dto.SecurityEventType;
import io.taktx.dto.SecurityMode;
import io.taktx.dto.SecurityPostureIssueCodes;
import io.taktx.security.ParticipantStatusSupport;
import jakarta.annotation.Nullable;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Simplified operator-facing namespace posture view derived from the existing public posture
 * surface.
 *
 * <p>This model is intentionally additive: it clarifies requested-vs-effective posture without
 * changing runtime trust or activation authority semantics.
 */
public record SimplifiedSecurityPostureSnapshot(
    SecurityMode requestedPosture,
    SecurityMode effectivePosture,
    SecurityRequestStatus requestStatus,
    boolean protectedRuntimeAllowed,
    ParticipantSummary participantSummary,
    List<BlockingIssue> blockingIssues,
    Map<SecurityMode, TargetModeFeasibility> targetModeFeasibility,
    SecurityPostureSnapshot sourceSnapshot) {

  public SimplifiedSecurityPostureSnapshot {
    requestedPosture = requestedPosture != null ? requestedPosture : SecurityMode.OPEN;
    effectivePosture = effectivePosture != null ? effectivePosture : SecurityMode.OPEN;
    requestStatus = requestStatus != null ? requestStatus : SecurityRequestStatus.IN_SYNC;
    participantSummary =
        participantSummary != null
            ? participantSummary
            : ParticipantSummary.from(Map.of(), null, null, System.currentTimeMillis());
    blockingIssues = blockingIssues == null ? List.of() : List.copyOf(blockingIssues);
    targetModeFeasibility =
        targetModeFeasibility == null ? Map.of() : Map.copyOf(targetModeFeasibility);
    sourceSnapshot = sourceSnapshot != null ? sourceSnapshot : SecurityPostureSnapshot.empty();
  }

  public static SimplifiedSecurityPostureSnapshot empty() {
    return from(
        SecurityPostureSnapshot.empty(), AuthoritativePolicyMutationAvailability.notObserved());
  }

  public static SimplifiedSecurityPostureSnapshot from(@Nullable SecurityPostureSnapshot snapshot) {
    return from(snapshot, AuthoritativePolicyMutationAvailability.notObserved());
  }

  public static SimplifiedSecurityPostureSnapshot from(
      @Nullable SecurityPostureSnapshot snapshot,
      @Nullable AuthoritativePolicyMutationAvailability mutationAvailability) {
    SecurityPostureSnapshot effectiveSnapshot =
        snapshot != null ? snapshot : SecurityPostureSnapshot.empty();
    AuthoritativePolicyMutationAvailability effectiveMutationAvailability =
        mutationAvailability != null
            ? mutationAvailability
            : AuthoritativePolicyMutationAvailability.notObserved();
    long nowMs = System.currentTimeMillis();
    SecurityMode requestedPosture =
        defaultToOpen(effectiveSnapshot.observedPolicy().requestedMode());
    SecurityMode effectivePosture = defaultToOpen(effectiveSnapshot.observedPolicy().activeMode());
    Long activePolicyVersion = effectiveSnapshot.observedPolicy().activePolicyVersion();
    String activePolicyHash = effectiveSnapshot.observedPolicy().activePolicyHash();
    ParticipantSummary participantSummary =
        ParticipantSummary.from(
            effectiveSnapshot.participantStatuses(), activePolicyVersion, activePolicyHash, nowMs);
    List<BlockingIssue> blockingIssues = flattenBlockingIssues(effectiveSnapshot, nowMs);
    SecurityRequestStatus requestStatus =
        deriveRequestStatus(effectiveSnapshot, requestedPosture, effectivePosture, blockingIssues);
    boolean protectedRuntimeAllowed =
        deriveProtectedRuntimeAllowed(effectivePosture, participantSummary);
    Map<SecurityMode, TargetModeFeasibility> targetModeFeasibility =
        deriveTargetModeFeasibility(
            effectiveSnapshot, effectiveMutationAvailability, blockingIssues, nowMs);
    return new SimplifiedSecurityPostureSnapshot(
        requestedPosture,
        effectivePosture,
        requestStatus,
        protectedRuntimeAllowed,
        participantSummary,
        blockingIssues,
        targetModeFeasibility,
        effectiveSnapshot);
  }

  public boolean hasBlockingIssues() {
    return !blockingIssues.isEmpty();
  }

  public TargetModeFeasibility targetModeFeasibility(SecurityMode targetMode) {
    SecurityMode effectiveTargetMode = defaultToOpen(targetMode);
    return targetModeFeasibility.getOrDefault(
        effectiveTargetMode, TargetModeFeasibility.feasible(effectiveTargetMode));
  }

  private static List<BlockingIssue> flattenBlockingIssues(
      SecurityPostureSnapshot snapshot, long nowMs) {
    Map<String, BlockingIssue> issues = new LinkedHashMap<>();

    snapshot
        .participantStatuses()
        .forEach((participantInstanceId, status) -> addStatusIssues(issues, status, nowMs));
    for (ParticipantPostureMismatch mismatch : snapshot.mismatchReasons()) {
      if (mismatch == null || mismatch.mismatchReason() == null) {
        continue;
      }
      ParticipantStatusDTO status = snapshot.participantStatus(mismatch.participantInstanceId());
      if (!isBlockingRelevant(status) || !isCurrentlyBlocking(status, nowMs)) {
        continue;
      }
      addIssue(
          issues,
          new BlockingIssue(
              BlockingIssueSource.PARTICIPANT_STATUS,
              mismatch.mismatchReason().getCode(),
              mismatch.mismatchReason().getMessage(),
              mismatch.participantInstanceId(),
              mismatch.participantId(),
              mismatch.participantKind(),
              mismatch.componentType(),
              null,
              null,
              mismatch.mismatchReason().getMetadata()));
    }
    for (SecurityEventDTO event : snapshot.recentSecurityEvents()) {
      if (event == null || !isBlockingEvent(event.getEventType())) {
        continue;
      }
      addIssue(
          issues,
          new BlockingIssue(
              BlockingIssueSource.SECURITY_EVENT,
              event.getCode(),
              event.getMessage(),
              event.getParticipantInstanceId(),
              event.getParticipantId(),
              null,
              null,
              event.getEventType(),
              event.getSeverity(),
              event.getMetadata()));
    }
    return List.copyOf(issues.values());
  }

  private static void addStatusIssues(
      Map<String, BlockingIssue> issues, @Nullable ParticipantStatusDTO status, long nowMs) {
    if (status == null || !isBlockingRelevant(status)) {
      return;
    }
    if (ParticipantStatusSupport.isExpired(status, nowMs)
        || status.getEffectiveState() == ParticipantEffectiveState.STALE) {
      addIssue(
          issues,
          new BlockingIssue(
              BlockingIssueSource.DERIVED,
              SecurityPostureIssueCodes.PARTICIPANT_STATUS_STALE,
              "Participant status is stale or expired and can no longer be treated as current posture",
              status.getParticipantInstanceId(),
              status.getParticipantId(),
              status.getParticipantKind(),
              status.getComponentType(),
              null,
              null,
              Map.of("statusExpiresAt", String.valueOf(status.getStatusExpiresAt()))));
      return;
    }

    if (status.getMismatchReasons() != null && !status.getMismatchReasons().isEmpty()) {
      return;
    }

    if (status.getEffectiveState() == ParticipantEffectiveState.NOT_READY
        || status.getEffectiveState() == ParticipantEffectiveState.MISMATCH
        || !status.isReadyForDataPlane()) {
      addIssue(
          issues,
          new BlockingIssue(
              BlockingIssueSource.DERIVED,
              SecurityPostureIssueCodes.PARTICIPANT_NOT_READY,
              "Participant is not currently ready for protected data-plane participation",
              status.getParticipantInstanceId(),
              status.getParticipantId(),
              status.getParticipantKind(),
              status.getComponentType(),
              null,
              null,
              Map.of(
                  "effectiveState", String.valueOf(status.getEffectiveState()),
                  "readyForDataPlane", Boolean.toString(status.isReadyForDataPlane()))));
    }
  }

  /**
   * Whether a participant's self-reported mismatch reasons should be treated as <em>current</em>
   * blocking issues.
   *
   * <p>Expired or {@code STALE} statuses are excluded here — staleness is reported separately as a
   * single derived {@code PARTICIPANT_STATUS_STALE} issue (see {@link #addStatusIssues}), so
   * surfacing their leftover (possibly ANCHORED-era) mismatch reasons as live blockers would be
   * misleading. A participant that is {@code READY} and {@code readyForDataPlane} reports its
   * mismatch reasons as informational warnings (e.g. OPEN-mode signing gaps that would only matter
   * under ANCHORED), not as conditions blocking it right now.
   */
  private static boolean isCurrentlyBlocking(@Nullable ParticipantStatusDTO status, long nowMs) {
    if (status == null) {
      return false;
    }
    if (ParticipantStatusSupport.isExpired(status, nowMs)
        || status.getEffectiveState() == ParticipantEffectiveState.STALE) {
      return false;
    }
    return !(status.isReadyForDataPlane()
        && status.getEffectiveState() == ParticipantEffectiveState.READY);
  }

  private static boolean isBlockingRelevant(@Nullable ParticipantStatusDTO status) {
    if (status == null || status.getCapabilities() == null || status.getCapabilities().isEmpty()) {
      return false;
    }
    Set<ParticipantCapability> capabilities = status.getCapabilities();
    return capabilities.contains(ParticipantCapability.ENFORCER)
        || capabilities.contains(ParticipantCapability.PROTECTED_RUNTIME_PARTICIPANT);
  }

  private static boolean isBlockingEvent(@Nullable SecurityEventType eventType) {
    return eventType == SecurityEventType.POLICY_REJECTION
        || eventType == SecurityEventType.READINESS_MISMATCH
        || eventType == SecurityEventType.ACTIVATION_TIMEOUT
        || eventType == SecurityEventType.ACTIVATION_ROLLBACK
        || eventType == SecurityEventType.CONTROL_PLANE_MUTATION_REJECTED
        || eventType == SecurityEventType.DATA_PLANE_BLOCKED
        || eventType == SecurityEventType.TRUST_ANCHOR_PROBLEM;
  }

  private static Map<SecurityMode, TargetModeFeasibility> deriveTargetModeFeasibility(
      SecurityPostureSnapshot snapshot,
      AuthoritativePolicyMutationAvailability mutationAvailability,
      List<BlockingIssue> currentBlockingIssues,
      long nowMs) {
    Map<SecurityMode, TargetModeFeasibility> feasibility = new EnumMap<>(SecurityMode.class);
    feasibility.put(SecurityMode.OPEN, TargetModeFeasibility.feasible(SecurityMode.OPEN));
    feasibility.put(
        SecurityMode.ANCHORED,
        feasibilityFor(
            SecurityMode.ANCHORED, snapshot, mutationAvailability, currentBlockingIssues, nowMs));
    return Map.copyOf(feasibility);
  }

  private static TargetModeFeasibility feasibilityFor(
      SecurityMode targetMode,
      SecurityPostureSnapshot snapshot,
      AuthoritativePolicyMutationAvailability mutationAvailability,
      List<BlockingIssue> currentBlockingIssues,
      long nowMs) {
    Map<String, BlockingIssue> blockers = new LinkedHashMap<>();
    if (mutationAvailability != null
        && mutationAvailability.observed()
        && !mutationAvailability.available()) {
      addIssue(
          blockers,
          new BlockingIssue(
              BlockingIssueSource.DERIVED,
              mutationAvailability.code(),
              mutationAvailability.message(),
              null,
              null,
              null,
              null,
              null,
              null,
              mutationAvailability.metadata()));
    }

    for (ParticipantStatusDTO status : snapshot.participantStatuses().values()) {
      if (status == null || !isBlockingRelevant(status)) {
        continue;
      }
      if (ParticipantStatusSupport.isExpired(status, nowMs)
          || status.getEffectiveState() == ParticipantEffectiveState.STALE) {
        addIssue(
            blockers,
            new BlockingIssue(
                BlockingIssueSource.DERIVED,
                SecurityPostureIssueCodes.PARTICIPANT_STATUS_STALE,
                "Participant status is stale or expired and cannot be used for target-mode feasibility.",
                status.getParticipantInstanceId(),
                status.getParticipantId(),
                status.getParticipantKind(),
                status.getComponentType(),
                null,
                null,
                Map.of(
                    "statusExpiresAt", String.valueOf(status.getStatusExpiresAt()),
                    "targetMode", targetMode.name())));
        continue;
      }
      if (!ParticipantStatusSupport.supportsMode(status, targetMode)) {
        addIssue(
            blockers,
            new BlockingIssue(
                BlockingIssueSource.PARTICIPANT_STATUS,
                SecurityPostureIssueCodes.TARGET_MODE_UNSUPPORTED,
                "Participant does not advertise support for target mode " + targetMode,
                status.getParticipantInstanceId(),
                status.getParticipantId(),
                status.getParticipantKind(),
                status.getComponentType(),
                null,
                null,
                Map.of(
                    "targetMode", targetMode.name(),
                    "supportedModes",
                        ParticipantStatusSupport.supportedModes(status).stream()
                            .map(SecurityMode::name)
                            .sorted()
                            .reduce((left, right) -> left + "," + right)
                            .orElse(""))));
      }
    }

    if (targetMode == snapshot.observedPolicy().activeMode()) {
      currentBlockingIssues.forEach(issue -> addIssue(blockers, issue));
    }

    return blockers.isEmpty()
        ? TargetModeFeasibility.feasible(targetMode)
        : TargetModeFeasibility.blocked(targetMode, List.copyOf(blockers.values()));
  }

  private static SecurityRequestStatus deriveRequestStatus(
      SecurityPostureSnapshot snapshot,
      SecurityMode requestedPosture,
      SecurityMode effectivePosture,
      List<BlockingIssue> blockingIssues) {
    if (sameRequestedAndEffective(snapshot, requestedPosture, effectivePosture)) {
      return SecurityRequestStatus.IN_SYNC;
    }
    SecurityActivationState activationState = snapshot.currentActivationState();
    if (activationState == SecurityActivationState.REQUESTED) {
      return SecurityRequestStatus.REQUESTED;
    }
    if (activationState == SecurityActivationState.VALIDATING) {
      return blockingIssues.isEmpty()
          ? SecurityRequestStatus.VALIDATING
          : SecurityRequestStatus.BLOCKED;
    }
    if (requestedPosture != effectivePosture && !blockingIssues.isEmpty()) {
      return SecurityRequestStatus.BLOCKED;
    }
    return SecurityRequestStatus.IN_SYNC;
  }

  private static boolean deriveProtectedRuntimeAllowed(
      SecurityMode effectivePosture, ParticipantSummary participantSummary) {
    if (effectivePosture == SecurityMode.OPEN) {
      return true;
    }
    if (participantSummary.protectedRuntimeParticipants() > 0) {
      return !participantSummary.hasProtectedRuntimeBlockers();
    }
    if (participantSummary.activationRelevantParticipants() > 0) {
      return !participantSummary.hasActivationRelevantBlockers();
    }
    return false;
  }

  private static boolean sameRequestedAndEffective(
      SecurityPostureSnapshot snapshot,
      SecurityMode requestedPosture,
      SecurityMode effectivePosture) {
    return requestedPosture == effectivePosture
        && java.util.Objects.equals(
            snapshot.observedPolicy().requestedPolicyVersion(),
            snapshot.observedPolicy().activePolicyVersion())
        && java.util.Objects.equals(
            snapshot.observedPolicy().requestedPolicyHash(),
            snapshot.observedPolicy().activePolicyHash());
  }

  private static void addIssue(Map<String, BlockingIssue> issues, BlockingIssue issue) {
    String key =
        issue.source()
            + "|"
            + issue.code()
            + "|"
            + String.valueOf(issue.participantInstanceId())
            + "|"
            + String.valueOf(issue.eventType());
    issues.putIfAbsent(key, issue);
  }

  private static SecurityMode defaultToOpen(@Nullable SecurityMode mode) {
    return mode != null ? mode : SecurityMode.OPEN;
  }
}
