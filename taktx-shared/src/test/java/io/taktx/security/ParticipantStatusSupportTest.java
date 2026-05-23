/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.taktx.dto.ParticipantEffectiveState;
import io.taktx.dto.ParticipantRole;
import io.taktx.dto.ParticipantStatusDTO;
import io.taktx.dto.PolicyMismatchReasonDTO;
import io.taktx.dto.StatusVerificationLevel;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ParticipantStatusSupportTest {

  @Test
  void normalize_replacesNullMismatchMetadataWithEmptyMap() {
    ParticipantStatusDTO normalized =
        ParticipantStatusSupport.normalize(
            ParticipantStatusDTO.builder()
                .participantId("engine-1")
                .participantInstanceId("engine-1-pod")
                .role(ParticipantRole.ENGINE)
                .namespace("bank.payments")
                .startedAt(100L)
                .lastSeenAt(150L)
                .statusExpiresAt(200L)
                .statusVerificationLevel(StatusVerificationLevel.UNVERIFIED_STATUS)
                .effectiveState(ParticipantEffectiveState.MISMATCH)
                .mismatchReasons(
                    java.util.List.of(
                        PolicyMismatchReasonDTO.builder()
                            .code("TRUST_ANCHOR_MISSING")
                            .message("missing platform public key")
                            .metadata(null)
                            .build()))
                .build());

    assertThat(normalized.getMismatchReasons()).hasSize(1);
    assertThat(normalized.getMismatchReasons().getFirst().getMetadata()).isEqualTo(Map.of());
  }

  @Test
  void requireValid_acceptsWellFormedReadyStatus() {
    ParticipantStatusDTO validated =
        ParticipantStatusSupport.requireValid(
            ParticipantStatusDTO.builder()
                .participantId("engine-1")
                .participantInstanceId("engine-1-pod")
                .role(ParticipantRole.ENGINE)
                .namespace("bank.payments")
                .startedAt(100L)
                .lastSeenAt(150L)
                .statusExpiresAt(200L)
                .statusVerificationLevel(StatusVerificationLevel.LOCALLY_VERIFIED_STATUS)
                .effectiveState(ParticipantEffectiveState.READY)
                .readyForDataPlane(true)
                .observedPolicyVersion(42L)
                .observedPolicyHash("abc123")
                .build());

    assertThat(validated.isReadyForDataPlane()).isTrue();
    assertThat(validated.getObservedPolicyVersion()).isEqualTo(42L);
  }

  @Test
  void requireValid_rejectsMissingTtlAndInstanceIdentity() {
    ParticipantStatusDTO status =
        ParticipantStatusDTO.builder()
            .participantId("engine-1")
            .role(ParticipantRole.ENGINE)
            .namespace("bank.payments")
            .startedAt(100L)
            .lastSeenAt(50L)
            .statusVerificationLevel(StatusVerificationLevel.UNVERIFIED_STATUS)
            .effectiveState(ParticipantEffectiveState.MISMATCH)
            .build();

    assertThatThrownBy(() -> ParticipantStatusSupport.requireValid(status))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("participantInstanceId must not be blank")
        .hasMessageContaining("statusExpiresAt must be > 0")
        .hasMessageContaining("lastSeenAt must be >= startedAt");
  }

  @Test
  void requireValid_rejectsReadyFlagWithoutReadyState() {
    ParticipantStatusDTO status =
        ParticipantStatusDTO.builder()
            .participantId("engine-1")
            .participantInstanceId("engine-1-pod")
            .role(ParticipantRole.ENGINE)
            .namespace("bank.payments")
            .startedAt(100L)
            .lastSeenAt(150L)
            .statusExpiresAt(200L)
            .statusVerificationLevel(StatusVerificationLevel.UNVERIFIED_STATUS)
            .effectiveState(ParticipantEffectiveState.MISMATCH)
            .readyForDataPlane(true)
            .build();

    assertThatThrownBy(() -> ParticipantStatusSupport.requireValid(status))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("readyForDataPlane=true requires effectiveState=READY");
  }

  @Test
  void isExpired_returnsTrueWhenStatusExpiryHasPassed() {
    ParticipantStatusDTO status =
        ParticipantStatusDTO.builder()
            .participantId("engine-1")
            .participantInstanceId("engine-1-pod")
            .role(ParticipantRole.ENGINE)
            .namespace("bank.payments")
            .startedAt(100L)
            .lastSeenAt(150L)
            .statusExpiresAt(200L)
            .statusVerificationLevel(StatusVerificationLevel.UNVERIFIED_STATUS)
            .effectiveState(ParticipantEffectiveState.STALE)
            .build();

    assertThat(ParticipantStatusSupport.isExpired(status, 199L)).isFalse();
    assertThat(ParticipantStatusSupport.isExpired(status, 200L)).isTrue();
  }
}
