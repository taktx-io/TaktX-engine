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
import io.taktx.dto.SecurityEventDTO;
import io.taktx.dto.SecurityEventType;
import io.taktx.dto.SecurityMode;
import io.taktx.dto.SecurityPostureIssueCodes;
import io.taktx.security.ParticipantStatusSupport;
import jakarta.annotation.Nullable;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Simplified operator-facing namespace posture view — read-only.
 *
 * <p>Mode is startup-static; it cannot be changed at runtime. This snapshot provides:
 *
 * <ul>
 *   <li>The effective mode (derived from participant self-reports or the policy topic)
 *   <li>Whether the namespace is currently ready for protected data-plane participation
 *   <li>A list of current blocking issues (transient key-publication window or signing gaps)
 * </ul>
 */
public record SimplifiedSecurityPostureSnapshot(
    @Nullable SecurityMode effectiveMode,
    boolean protectedRuntimeAllowed,
    ParticipantSummary participantSummary,
    List<BlockingIssue> blockingIssues,
    SecurityPostureSnapshot sourceSnapshot) {

  public SimplifiedSecurityPostureSnapshot {
    effectiveMode = effectiveMode != null ? effectiveMode : SecurityMode.OPEN;
    participantSummary =
        participantSummary != null
            ? participantSummary
            : ParticipantSummary.from(Map.of(), null, null, System.currentTimeMillis());
    blockingIssues = blockingIssues == null ? List.of() : List.copyOf(blockingIssues);
    sourceSnapshot = sourceSnapshot != null ? sourceSnapshot : SecurityPostureSnapshot.empty();
  }

  public static SimplifiedSecurityPostureSnapshot empty() {
    return from(SecurityPostureSnapshot.empty());
  }

  public static SimplifiedSecurityPostureSnapshot from(SecurityPostureSnapshot snapshot) {
    SecurityPostureSnapshot effectiveSnapshot =
        snapshot != null ? snapshot : SecurityPostureSnapshot.empty();
    long nowMs = System.currentTimeMillis();
    SecurityMode effectiveMode = effectiveSnapshot.effectiveMode();
    ParticipantSummary participantSummary =
        ParticipantSummary.from(effectiveSnapshot.participantStatuses(), null, null, nowMs);
    List<BlockingIssue> blockingIssues = flattenBlockingIssues(effectiveSnapshot, nowMs);
    boolean protectedRuntimeAllowed =
        deriveProtectedRuntimeAllowed(effectiveMode, participantSummary);
    return new SimplifiedSecurityPostureSnapshot(
        effectiveMode,
        protectedRuntimeAllowed,
        participantSummary,
        blockingIssues,
        effectiveSnapshot);
  }

  public boolean hasBlockingIssues() {
    return !blockingIssues.isEmpty();
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
              "Participant status is stale or expired",
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

  private static boolean deriveProtectedRuntimeAllowed(
      SecurityMode effectiveMode, ParticipantSummary participantSummary) {
    if (effectiveMode == SecurityMode.OPEN) {
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
}
