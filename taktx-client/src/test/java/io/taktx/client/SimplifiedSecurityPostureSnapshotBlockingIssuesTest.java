/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.client;

import static org.assertj.core.api.Assertions.assertThat;

import io.taktx.dto.ParticipantCapability;
import io.taktx.dto.ParticipantEffectiveState;
import io.taktx.dto.ParticipantKind;
import io.taktx.dto.ParticipantStatusDTO;
import io.taktx.dto.PolicyMismatchReasonDTO;
import io.taktx.dto.SecurityPostureIssueCodes;
import io.taktx.dto.StatusVerificationLevel;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Focused coverage for {@link SimplifiedSecurityPostureSnapshot}'s blocking-issue derivation, which
 * is the surface the Console renders as "blocking issues". Reproduces the two ways an ANCHORED-only
 * mismatch code can leak into the view while the namespace is effectively OPEN.
 */
class SimplifiedSecurityPostureSnapshotBlockingIssuesTest {

  private static final String ENGINE_SIGNING_UNAVAILABLE = "ENGINE_SIGNING_UNAVAILABLE";
  private static final String SIGNATURE_MISSING = "SIGNATURE_MISSING";

  @Test
  void staleEngineRecordDoesNotLeakAnchoredMismatchAsBlockingIssue() {
    // An engine record left behind in the compacted participant-status topic from a prior ANCHORED
    // period: effectiveState=MISMATCH, ENGINE_SIGNING_UNAVAILABLE, and already expired.
    ParticipantStatusDTO staleEngine =
        statusBuilder(
                "tenant.default.engine",
                "tenant.default@host:8080#111",
                Set.of(ParticipantCapability.ENFORCER, ParticipantCapability.SECURITY_OBSERVER))
            .effectiveState(ParticipantEffectiveState.MISMATCH)
            .readyForDataPlane(false)
            .statusExpiresAt(1_000L) // long in the past relative to now
            .mismatchReasons(
                List.of(
                    reason(ENGINE_SIGNING_UNAVAILABLE, "Engine key not yet published", "ERROR")))
            .build();

    SimplifiedSecurityPostureSnapshot simplified = simplifiedOpen(staleEngine);

    assertThat(blockingCodes(simplified)).doesNotContain(ENGINE_SIGNING_UNAVAILABLE);
    // Staleness is still surfaced exactly once via the derived code.
    assertThat(blockingCodes(simplified))
        .contains(SecurityPostureIssueCodes.PARTICIPANT_STATUS_STALE);
  }

  @Test
  void openModeWarningFromReadyParticipantIsNotABlockingIssue() {
    // A live ingester self-reporting under OPEN mode: READY + readyForDataPlane, with a
    // non-blocking
    // informational signing-gap warning attached (severity=WARNING).
    ParticipantStatusDTO readyIngester =
        statusBuilder(
                "tenant.default.ingester",
                "tenant.default.ingester@host#222",
                Set.of(ParticipantCapability.PROTECTED_RUNTIME_PARTICIPANT))
            .effectiveState(ParticipantEffectiveState.READY)
            .readyForDataPlane(true)
            .statusExpiresAt(farFuture())
            .mismatchReasons(
                List.of(reason(SIGNATURE_MISSING, "No signing identity configured", "WARNING")))
            .build();

    SimplifiedSecurityPostureSnapshot simplified = simplifiedOpen(readyIngester);

    assertThat(simplified.blockingIssues()).isEmpty();
  }

  @Test
  void genuineNotReadyParticipantStillSurfacesItsMismatchAsBlockingIssue() {
    // Regression guard: a participant that is actually MISMATCH / not ready under ANCHORED must
    // still produce its mismatch reason as a blocking issue.
    ParticipantStatusDTO blockedWorker =
        statusBuilder(
                "tenant.default.worker",
                "tenant.default.worker@host#333",
                Set.of(ParticipantCapability.PROTECTED_RUNTIME_PARTICIPANT))
            .effectiveState(ParticipantEffectiveState.MISMATCH)
            .readyForDataPlane(false)
            .statusExpiresAt(farFuture())
            .mismatchReasons(
                List.of(reason(ENGINE_SIGNING_UNAVAILABLE, "Signing identity not ready", "ERROR")))
            .build();

    SimplifiedSecurityPostureSnapshot simplified =
        SimplifiedSecurityPostureSnapshot.from(
            SecurityPostureSnapshot.from(
                Map.of(blockedWorker.getParticipantInstanceId(), blockedWorker),
                List.of()));

    assertThat(blockingCodes(simplified)).contains(ENGINE_SIGNING_UNAVAILABLE);
  }

  private static SimplifiedSecurityPostureSnapshot simplifiedOpen(ParticipantStatusDTO status) {
    return SimplifiedSecurityPostureSnapshot.from(
        SecurityPostureSnapshot.from(
            Map.of(status.getParticipantInstanceId(), status),
            List.of()));
  }

  private static List<String> blockingCodes(SimplifiedSecurityPostureSnapshot simplified) {
    return simplified.blockingIssues().stream().map(BlockingIssue::code).toList();
  }

  private static ParticipantStatusDTO.ParticipantStatusDTOBuilder statusBuilder(
      String participantId, String participantInstanceId, Set<ParticipantCapability> capabilities) {
    return ParticipantStatusDTO.builder()
        .participantId(participantId)
        .participantInstanceId(participantInstanceId)
        .participantKind(ParticipantKind.CLIENT)
        .componentType("test")
        .capabilities(capabilities)
        .namespace("default")
        .startedAt(1L)
        .lastSeenAt(1L)
        .statusVerificationLevel(StatusVerificationLevel.LOCALLY_VERIFIED_STATUS);
  }

  private static PolicyMismatchReasonDTO reason(String code, String message, String severity) {
    return PolicyMismatchReasonDTO.builder()
        .code(code)
        .message(message)
        .metadata(Map.of("severity", severity))
        .build();
  }

  private static long farFuture() {
    return System.currentTimeMillis() + 600_000L;
  }
}
