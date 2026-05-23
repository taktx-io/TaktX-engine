/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.serdes;

import static org.assertj.core.api.Assertions.assertThat;

import io.taktx.dto.ParticipantEffectiveState;
import io.taktx.dto.ParticipantRole;
import io.taktx.dto.ParticipantStatusDTO;
import io.taktx.dto.PolicyMismatchReasonDTO;
import io.taktx.dto.SecurityEventDTO;
import io.taktx.dto.SecurityEventSeverity;
import io.taktx.dto.SecurityEventType;
import io.taktx.dto.StatusVerificationLevel;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SecurityObservabilityProtoMapperTest {

  @Test
  void participantStatus_roundTripsThroughProto() {
    ParticipantStatusDTO dto =
        ParticipantStatusDTO.builder()
            .participantId("engine-2")
            .participantInstanceId("engine-2-pod-7f8c4d")
            .role(ParticipantRole.ENGINE)
            .namespace("bank.payments")
            .startedAt(1716450000000L)
            .lastSeenAt(1716450060000L)
            .statusExpiresAt(1716450120000L)
            .statusVerificationLevel(StatusVerificationLevel.UNVERIFIED_STATUS)
            .effectiveState(ParticipantEffectiveState.MISMATCH)
            .readyForDataPlane(false)
            .observedPolicyVersion(42L)
            .observedPolicyHash("abc123")
            .mismatchReasons(
                List.of(
                    PolicyMismatchReasonDTO.builder()
                        .code("TRUST_ANCHOR_MISSING")
                        .message(
                            "Namespace requires anchored trust but no platform public key is configured")
                        .metadata(Map.of("expectedMode", "anchored_secured"))
                        .build()))
            .build();

    assertThat(ParticipantStatusProtoMapper.toDto(ParticipantStatusProtoMapper.toProto(dto)))
        .isEqualTo(dto);
  }

  @Test
  void securityEvent_roundTripsThroughProto() {
    SecurityEventDTO dto =
        SecurityEventDTO.builder()
            .eventType(SecurityEventType.ACTIVATION_TIMEOUT)
            .severity(SecurityEventSeverity.ERROR)
            .occurredAtMs(1716450120000L)
            .namespace("bank.payments")
            .participantId("platform-service")
            .participantInstanceId("platform-service-1")
            .desiredPolicyVersion(42L)
            .desiredPolicyHash("abc123")
            .activePolicyVersion(41L)
            .activePolicyHash("def456")
            .code("ACTIVATION_TIMEOUT")
            .message("Requested policy remained in VALIDATING beyond the configured timeout")
            .metadata(Map.of("timeoutMs", "30000"))
            .build();

    assertThat(SecurityEventProtoMapper.toDto(SecurityEventProtoMapper.toProto(dto)))
        .isEqualTo(dto);
  }
}
