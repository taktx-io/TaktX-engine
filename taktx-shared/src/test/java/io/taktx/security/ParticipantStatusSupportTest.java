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

import io.taktx.dto.ParticipantCapability;
import io.taktx.dto.ParticipantEffectiveState;
import io.taktx.dto.ParticipantKind;
import io.taktx.dto.ParticipantStatusDTO;
import io.taktx.dto.PolicyMismatchReasonDTO;
import io.taktx.dto.SecurityMode;
import io.taktx.dto.StatusVerificationLevel;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ParticipantStatusSupportTest {

  @Test
  void normalize_replacesNullMismatchMetadataWithEmptyMap() {
    ParticipantStatusDTO normalized =
        ParticipantStatusSupport.normalize(
            ParticipantStatusDTO.builder()
                .participantId("engine-1")
                .participantInstanceId("engine-1-pod")
                .participantKind(ParticipantKind.ENGINE)
                .componentType("engine")
                .capabilities(Set.of(ParticipantCapability.ENFORCER))
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
  void normalize_replacesNullCapabilitySetsWithEmptySetsAndBlankOptionalComponentType() {
    ParticipantStatusDTO normalized =
        ParticipantStatusSupport.normalize(
            ParticipantStatusDTO.builder()
                .participantId("engine-1")
                .participantInstanceId("engine-1-pod")
                .participantKind(ParticipantKind.ENGINE)
                .componentType("   ")
                .capabilities(null)
                .namespace("bank.payments")
                .startedAt(100L)
                .lastSeenAt(150L)
                .statusExpiresAt(200L)
                .statusVerificationLevel(StatusVerificationLevel.UNVERIFIED_STATUS)
                .effectiveState(ParticipantEffectiveState.READY)
                .build());

    assertThat(normalized.getCapabilities()).isEmpty();
    assertThat(normalized.getComponentType()).isNull();
  }

  @Test
  void requireValid_acceptsWellFormedReadyStatus() {
    ParticipantStatusDTO validated =
        ParticipantStatusSupport.requireValid(
            ParticipantStatusDTO.builder()
                .participantId("engine-1")
                .participantInstanceId("engine-1-pod")
                .participantKind(ParticipantKind.ENGINE)
                .componentType("engine")
                .capabilities(
                    Set.of(ParticipantCapability.ENFORCER, ParticipantCapability.SECURITY_OBSERVER))
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
    assertThat(validated.getSupportedModes())
        .containsExactlyInAnyOrder(SecurityMode.OPEN, SecurityMode.ANCHORED);
  }

  @Test
  void normalize_derivesSupportedModesFromCapabilitiesWhenMissing() {
    ParticipantStatusDTO normalized =
        ParticipantStatusSupport.normalize(
            ParticipantStatusDTO.builder()
                .participantId("engine-1")
                .participantInstanceId("engine-1-pod")
                .participantKind(ParticipantKind.ENGINE)
                .componentType("engine")
                .capabilities(Set.of(ParticipantCapability.ENFORCER))
                .namespace("bank.payments")
                .startedAt(100L)
                .lastSeenAt(150L)
                .statusExpiresAt(200L)
                .statusVerificationLevel(StatusVerificationLevel.UNVERIFIED_STATUS)
                .effectiveState(ParticipantEffectiveState.READY)
                .build());

    assertThat(normalized.getSupportedModes())
        .containsExactlyInAnyOrder(SecurityMode.OPEN, SecurityMode.ANCHORED);
  }

  @Test
  void requireValid_rejectsMissingTtlAndInstanceIdentity() {
    ParticipantStatusDTO status =
        ParticipantStatusDTO.builder()
            .participantId("engine-1")
            .participantKind(ParticipantKind.ENGINE)
            .componentType("engine")
            .capabilities(Set.of(ParticipantCapability.ENFORCER))
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
  void requireValid_rejectsMissingKindAndEmptyCapabilities() {
    ParticipantStatusDTO status =
        ParticipantStatusDTO.builder()
            .participantId("participant-1")
            .participantInstanceId("participant-1#1")
            .componentType(" ")
            .capabilities(Set.of())
            .namespace("bank.payments")
            .startedAt(100L)
            .lastSeenAt(100L)
            .statusExpiresAt(200L)
            .statusVerificationLevel(StatusVerificationLevel.UNVERIFIED_STATUS)
            .effectiveState(ParticipantEffectiveState.NOT_READY)
            .build();

    assertThatThrownBy(() -> ParticipantStatusSupport.requireValid(status))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("participantKind must not be null")
        .hasMessageContaining("capabilities must not be empty");
  }

  @Test
  void requireValid_rejectsReadyFlagWithoutReadyState() {
    ParticipantStatusDTO status =
        ParticipantStatusDTO.builder()
            .participantId("engine-1")
            .participantInstanceId("engine-1-pod")
            .participantKind(ParticipantKind.ENGINE)
            .componentType("engine")
            .capabilities(Set.of(ParticipantCapability.ENFORCER))
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
  void requireValid_rejectsCapabilitySetsContainingNulls() {
    Set<ParticipantCapability> capabilities = new LinkedHashSet<>();
    capabilities.add(ParticipantCapability.ENFORCER);
    capabilities.add(null);

    ParticipantStatusDTO status =
        ParticipantStatusDTO.builder()
            .participantId("engine-1")
            .participantInstanceId("engine-1-pod")
            .participantKind(ParticipantKind.ENGINE)
            .componentType("engine")
            .capabilities(capabilities)
            .namespace("bank.payments")
            .startedAt(100L)
            .lastSeenAt(150L)
            .statusExpiresAt(200L)
            .statusVerificationLevel(StatusVerificationLevel.UNVERIFIED_STATUS)
            .effectiveState(ParticipantEffectiveState.NOT_READY)
            .build();

    assertThatThrownBy(() -> ParticipantStatusSupport.requireValid(status))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("capabilities must not contain null values");
  }

  @Test
  void requireValid_acceptsBlankOptionalComponentType() {
    ParticipantStatusDTO validated =
        ParticipantStatusSupport.requireValid(
            ParticipantStatusDTO.builder()
                .participantId("client-1")
                .participantInstanceId("client-1#1")
                .participantKind(ParticipantKind.CLIENT)
                .componentType("   ")
                .capabilities(Set.of(ParticipantCapability.PROTECTED_RUNTIME_PARTICIPANT))
                .namespace("bank.payments")
                .startedAt(100L)
                .lastSeenAt(150L)
                .statusExpiresAt(200L)
                .statusVerificationLevel(StatusVerificationLevel.UNVERIFIED_STATUS)
                .effectiveState(ParticipantEffectiveState.NOT_READY)
                .build());

    assertThat(validated.getComponentType()).isNull();
  }

  @Test
  void isExpired_returnsTrueWhenStatusExpiryHasPassed() {
    ParticipantStatusDTO status =
        ParticipantStatusDTO.builder()
            .participantId("engine-1")
            .participantInstanceId("engine-1-pod")
            .participantKind(ParticipantKind.ENGINE)
            .componentType("engine")
            .capabilities(Set.of(ParticipantCapability.ENFORCER))
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

  @Test
  void allowsProtectedDataPlaneParticipation_requiresReadyNonExpiredExactActiveIdentity() {
    ParticipantStatusDTO status =
        ParticipantStatusDTO.builder()
            .participantId("client-1")
            .participantInstanceId("client-1#1")
            .participantKind(ParticipantKind.CLIENT)
            .componentType("generic-client")
            .capabilities(Set.of(ParticipantCapability.PROTECTED_RUNTIME_PARTICIPANT))
            .namespace("bank.payments")
            .startedAt(100L)
            .lastSeenAt(150L)
            .statusExpiresAt(200L)
            .statusVerificationLevel(StatusVerificationLevel.LOCALLY_VERIFIED_STATUS)
            .effectiveState(ParticipantEffectiveState.READY)
            .readyForDataPlane(true)
            .observedPolicyVersion(42L)
            .observedPolicyHash("abc123")
            .build();

    assertThat(
            ParticipantStatusSupport.allowsProtectedDataPlaneParticipation(
                status, 42L, "abc123", 199L))
        .isTrue();
    assertThat(
            ParticipantStatusSupport.allowsProtectedDataPlaneParticipation(
                status, 43L, "abc123", 199L))
        .isFalse();
    assertThat(
            ParticipantStatusSupport.allowsProtectedDataPlaneParticipation(
                status, 42L, "different", 199L))
        .isFalse();
    assertThat(
            ParticipantStatusSupport.allowsProtectedDataPlaneParticipation(
                status, 42L, "abc123", 200L))
        .isFalse();
  }

  @Test
  void allowsProtectedDataPlaneParticipation_doesNotUseVerificationLevelAsTrustShortcut() {
    ParticipantStatusDTO unverifiedReady =
        ParticipantStatusDTO.builder()
            .participantId("client-1")
            .participantInstanceId("client-1#1")
            .participantKind(ParticipantKind.CLIENT)
            .componentType("generic-client")
            .capabilities(Set.of(ParticipantCapability.PROTECTED_RUNTIME_PARTICIPANT))
            .namespace("bank.payments")
            .startedAt(100L)
            .lastSeenAt(150L)
            .statusExpiresAt(200L)
            .statusVerificationLevel(StatusVerificationLevel.UNVERIFIED_STATUS)
            .effectiveState(ParticipantEffectiveState.READY)
            .readyForDataPlane(true)
            .observedPolicyVersion(42L)
            .observedPolicyHash("abc123")
            .build();
    ParticipantStatusDTO verifiedNotReady =
        unverifiedReady.toBuilder()
            .statusVerificationLevel(StatusVerificationLevel.LOCALLY_VERIFIED_STATUS)
            .effectiveState(ParticipantEffectiveState.NOT_READY)
            .readyForDataPlane(false)
            .build();

    assertThat(
            ParticipantStatusSupport.allowsProtectedDataPlaneParticipation(
                unverifiedReady, 42L, "abc123", 199L))
        .isTrue();
    assertThat(
            ParticipantStatusSupport.allowsProtectedDataPlaneParticipation(
                verifiedNotReady, 42L, "abc123", 199L))
        .isFalse();
  }

  @Test
  void supportHelpers_distinguishSupportInPrincipleFromCurrentReadiness() {
    ParticipantStatusDTO status =
        ParticipantStatusDTO.builder()
            .participantId("console-1")
            .participantInstanceId("console-1#1")
            .participantKind(ParticipantKind.CLIENT)
            .componentType("console")
            .capabilities(
                Set.of(
                    ParticipantCapability.AUTHORITATIVE_POLICY_PUBLISHER,
                    ParticipantCapability.PROTECTED_RUNTIME_PARTICIPANT,
                    ParticipantCapability.SECURITY_OBSERVER))
            .namespace("bank.payments")
            .startedAt(100L)
            .lastSeenAt(150L)
            .statusExpiresAt(200L)
            .statusVerificationLevel(StatusVerificationLevel.LOCALLY_VERIFIED_STATUS)
            .effectiveState(ParticipantEffectiveState.MISMATCH)
            .readyForDataPlane(false)
            .supportedModes(Set.of(SecurityMode.OPEN, SecurityMode.ANCHORED))
            .build();

    assertThat(ParticipantStatusSupport.supportsMode(status, SecurityMode.ANCHORED)).isTrue();
    assertThat(ParticipantStatusSupport.supportsProtectedRuntimeParticipation(status)).isTrue();
    assertThat(ParticipantStatusSupport.supportsAuthoritativePolicyPublication(status)).isTrue();
    assertThat(ParticipantStatusSupport.supportsTrustAnchorValidation(status)).isTrue();
    assertThat(
            ParticipantStatusSupport.allowsProtectedDataPlaneParticipation(
                status, 42L, "abc123", 199L))
        .isFalse();
  }
}
