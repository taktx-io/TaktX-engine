/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.engine.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.taktx.dto.NamespaceSecurityPolicyDTO;
import io.taktx.dto.ParticipantCapability;
import io.taktx.dto.ParticipantEffectiveState;
import io.taktx.dto.ParticipantKind;
import io.taktx.dto.ParticipantStatusDTO;
import io.taktx.dto.RequiredSigningDTO;
import io.taktx.dto.SecurityActivationState;
import io.taktx.dto.SecurityEventDTO;
import io.taktx.dto.SecurityEventType;
import io.taktx.dto.SecurityMode;
import io.taktx.dto.StatusVerificationLevel;
import io.taktx.engine.config.NamespaceSecurityPolicyStore;
import io.taktx.engine.config.ParticipantStatusStore;
import io.taktx.engine.config.TaktConfiguration;
import io.taktx.security.NamespaceSecurityPolicySupport;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

class NamespaceSecurityPolicyActivationServiceTest {

  private static final Set<ParticipantCapability> ENGINE_CAPABILITIES =
      Set.of(ParticipantCapability.ENFORCER, ParticipantCapability.SECURITY_OBSERVER);
  private static final Set<ParticipantCapability> CONTROL_PLANE_CLIENT_CAPABILITIES =
      Set.of(
          ParticipantCapability.AUTHORITATIVE_POLICY_PUBLISHER,
          ParticipantCapability.SECURITY_OBSERVER);
  private static final Set<ParticipantCapability> PROTECTED_RUNTIME_CLIENT_CAPABILITIES =
      Set.of(ParticipantCapability.PROTECTED_RUNTIME_PARTICIPANT);

  private TaktConfiguration configuration;
  private NamespaceSecurityPolicyStore policyStore;
  private ParticipantStatusStore participantStatusStore;
  private SecurityEventPublisher securityEventPublisher;
  private MutableClock clock;
  private NamespaceSecurityPolicyActivationService activationService;

  @BeforeEach
  void setUp() {
    configuration = Mockito.mock(TaktConfiguration.class);
    when(configuration.getTenantId()).thenReturn("tenant");
    when(configuration.getNamespace()).thenReturn("bank.payments");
    when(configuration.getHost()).thenReturn("engine-host");
    when(configuration.getPort()).thenReturn(8080);

    policyStore = new NamespaceSecurityPolicyStore();
    participantStatusStore = new ParticipantStatusStore();
    securityEventPublisher = Mockito.mock(SecurityEventPublisher.class);
    clock = new MutableClock(1_716_450_000_000L);
    activationService =
        new NamespaceSecurityPolicyActivationService(
            configuration,
            policyStore,
            participantStatusStore,
            securityEventPublisher,
            clock,
            1_000L);
  }

  @Test
  void requestedPolicy_transitionsFromValidatingToActiveWhenRequiredParticipantsConverge() {
    NamespaceSecurityPolicyDTO requested = requestedPolicy(42L);
    addReadyEnforcerParticipant(requested, clock.millis() + 500L);

    activationService.onPolicyUpdated(requested);

    assertThat(policyStore.get()).isNotNull();
    assertThat(policyStore.get().getActivationState()).isEqualTo(SecurityActivationState.ACTIVE);
    assertThat(policyStore.get().getActivePolicyVersion()).isEqualTo(42L);
    assertThat(policyStore.get().getActivePolicyHash())
        .isEqualTo(policyStore.get().getDesiredPolicyHash());
    assertThat(policyStore.getActivePolicy()).isEqualTo(policyStore.get());
    verify(securityEventPublisher, never()).publish(any(SecurityEventDTO.class));
  }

  @Test
  void anchoredRequestedPolicy_transitionsToActiveWhenRequiredParticipantsConverge() {
    NamespaceSecurityPolicyDTO requested = anchoredRequestedPolicy(84L);
    addReadyEnforcerParticipant(requested, clock.millis() + 500L);

    activationService.onPolicyUpdated(requested);

    assertThat(policyStore.get()).isNotNull();
    assertThat(policyStore.get().getActivationState()).isEqualTo(SecurityActivationState.ACTIVE);
    assertThat(policyStore.get().getMode()).isEqualTo(SecurityMode.ANCHORED_SECURED);
    assertThat(policyStore.get().isTrustAnchorRequired()).isTrue();
    assertThat(policyStore.get().getActivePolicyVersion()).isEqualTo(84L);
    assertThat(policyStore.get().getActivePolicyHash()).isEqualTo(requested.getDesiredPolicyHash());
    verify(securityEventPublisher, never()).publish(any(SecurityEventDTO.class));
  }

  @Test
  void missingOrExpiredRequiredParticipants_keepPolicyValidatingUntilTimeout() {
    NamespaceSecurityPolicyDTO requested = requestedPolicy(42L);
    participantStatusStore.update(
        "engine-1",
        ParticipantStatusDTO.builder()
            .participantId("tenant.bank.payments.engine")
            .participantInstanceId("engine-1")
            .participantKind(ParticipantKind.ENGINE)
            .componentType("engine")
            .capabilities(ENGINE_CAPABILITIES)
            .namespace("bank.payments")
            .startedAt(clock.millis() - 200L)
            .lastSeenAt(clock.millis() - 100L)
            .statusExpiresAt(clock.millis() - 1L)
            .statusVerificationLevel(StatusVerificationLevel.LOCALLY_VERIFIED_STATUS)
            .effectiveState(ParticipantEffectiveState.READY)
            .readyForDataPlane(true)
            .observedPolicyVersion(requested.getDesiredPolicyVersion())
            .observedPolicyHash(requested.getDesiredPolicyHash())
            .build());

    activationService.onPolicyUpdated(requested);

    assertThat(policyStore.get()).isNotNull();
    assertThat(policyStore.get().getActivationState())
        .isEqualTo(SecurityActivationState.VALIDATING);
    assertThat(policyStore.getValidationStartedAtMs()).isEqualTo(clock.millis());
    verify(securityEventPublisher, never()).publish(any(SecurityEventDTO.class));
  }

  @Test
  void timeout_rollsBackToPreviousActivePolicyAndPublishesTimeoutAndRejectionEvents() {
    NamespaceSecurityPolicyDTO previousActive = activePolicy(41L);
    policyStore.update(previousActive);

    NamespaceSecurityPolicyDTO requested = requestedPolicy(42L);
    activationService.onPolicyUpdated(requested);

    assertThat(policyStore.get().getActivationState())
        .isEqualTo(SecurityActivationState.VALIDATING);

    clock.advanceMillis(1_001L);
    activationService.reevaluate();

    assertThat(policyStore.get()).isEqualTo(previousActive);
    assertThat(policyStore.getActivePolicy()).isEqualTo(previousActive);

    ArgumentCaptor<SecurityEventDTO> captor = ArgumentCaptor.forClass(SecurityEventDTO.class);
    verify(securityEventPublisher, Mockito.times(2)).publish(captor.capture());
    assertThat(captor.getAllValues())
        .extracting(SecurityEventDTO::getEventType)
        .containsExactly(SecurityEventType.ACTIVATION_TIMEOUT, SecurityEventType.POLICY_REJECTION);
    assertThat(captor.getAllValues().getFirst().getMetadata()).containsEntry("timeoutMs", "1000");
  }

  @Test
  void readinessMismatch_rejectsPolicyAndPreservesPreviousActivePolicy() {
    NamespaceSecurityPolicyDTO previousActive = activePolicy(41L);
    policyStore.update(previousActive);

    NamespaceSecurityPolicyDTO requested = requestedPolicy(42L);
    participantStatusStore.update(
        "engine-1",
        ParticipantStatusDTO.builder()
            .participantId("tenant.bank.payments.engine")
            .participantInstanceId("engine-1")
            .participantKind(ParticipantKind.ENGINE)
            .componentType("engine")
            .capabilities(ENGINE_CAPABILITIES)
            .namespace("bank.payments")
            .startedAt(clock.millis() - 100L)
            .lastSeenAt(clock.millis())
            .statusExpiresAt(clock.millis() + 500L)
            .statusVerificationLevel(StatusVerificationLevel.LOCALLY_VERIFIED_STATUS)
            .effectiveState(ParticipantEffectiveState.READY)
            .readyForDataPlane(true)
            .observedPolicyVersion(42L)
            .observedPolicyHash("different-hash")
            .build());
    participantStatusStore.update(
        "observer-1",
        ParticipantStatusDTO.builder()
            .participantId("tenant.bank.payments.console")
            .participantInstanceId("observer-1")
            .participantKind(ParticipantKind.CLIENT)
            .componentType("console")
            .capabilities(CONTROL_PLANE_CLIENT_CAPABILITIES)
            .namespace("bank.payments")
            .startedAt(clock.millis() - 100L)
            .lastSeenAt(clock.millis())
            .statusExpiresAt(clock.millis() + 500L)
            .statusVerificationLevel(StatusVerificationLevel.LOCALLY_VERIFIED_STATUS)
            .effectiveState(ParticipantEffectiveState.READY)
            .readyForDataPlane(true)
            .observedPolicyVersion(42L)
            .observedPolicyHash("different-hash")
            .build());

    activationService.onPolicyUpdated(requested);

    assertThat(policyStore.get()).isEqualTo(previousActive);
    ArgumentCaptor<SecurityEventDTO> captor = ArgumentCaptor.forClass(SecurityEventDTO.class);
    verify(securityEventPublisher, Mockito.times(2)).publish(captor.capture());
    assertThat(captor.getAllValues())
        .extracting(SecurityEventDTO::getEventType)
        .containsExactly(SecurityEventType.READINESS_MISMATCH, SecurityEventType.POLICY_REJECTION);
    assertThat(captor.getAllValues().getFirst().getMetadata())
        .containsEntry("policyMismatchParticipants", "engine-1");
  }

  @Test
  void notReadyRequiredParticipant_rejectsPolicyAndIdentifiesBlockingParticipant() {
    NamespaceSecurityPolicyDTO requested = requestedPolicy(42L);
    participantStatusStore.update(
        "engine-1",
        ParticipantStatusDTO.builder()
            .participantId("tenant.bank.payments.engine")
            .participantInstanceId("engine-1")
            .participantKind(ParticipantKind.ENGINE)
            .componentType("engine")
            .capabilities(ENGINE_CAPABILITIES)
            .namespace("bank.payments")
            .startedAt(clock.millis() - 100L)
            .lastSeenAt(clock.millis())
            .statusExpiresAt(clock.millis() + 500L)
            .statusVerificationLevel(StatusVerificationLevel.LOCALLY_VERIFIED_STATUS)
            .effectiveState(ParticipantEffectiveState.MISMATCH)
            .readyForDataPlane(false)
            .observedPolicyVersion(requested.getDesiredPolicyVersion())
            .observedPolicyHash(requested.getDesiredPolicyHash())
            .build());

    activationService.onPolicyUpdated(requested);

    assertThat(policyStore.get()).isNull();
    assertThat(policyStore.getActivePolicy()).isNull();
    ArgumentCaptor<SecurityEventDTO> captor = ArgumentCaptor.forClass(SecurityEventDTO.class);
    verify(securityEventPublisher, Mockito.times(2)).publish(captor.capture());
    assertThat(captor.getAllValues())
        .extracting(SecurityEventDTO::getEventType)
        .containsExactly(SecurityEventType.READINESS_MISMATCH, SecurityEventType.POLICY_REJECTION);
    assertThat(captor.getAllValues().getFirst().getMetadata())
        .containsEntry("notReadyParticipants", "engine-1")
        .doesNotContainKey("policyMismatchParticipants");
  }

  @Test
  void duplicateRequestedPolicyDoesNotResetValidationTimeoutWindow() {
    NamespaceSecurityPolicyDTO requested = requestedPolicy(42L);

    activationService.onPolicyUpdated(requested);
    Long firstStartedAt = policyStore.getValidationStartedAtMs();

    clock.advanceMillis(900L);
    activationService.onPolicyUpdated(requested);

    assertThat(policyStore.getValidationStartedAtMs()).isEqualTo(firstStartedAt);

    clock.advanceMillis(101L);
    activationService.reevaluate();

    assertThat(policyStore.get()).isNull();
    ArgumentCaptor<SecurityEventDTO> captor = ArgumentCaptor.forClass(SecurityEventDTO.class);
    verify(securityEventPublisher, Mockito.times(2)).publish(captor.capture());
    assertThat(captor.getAllValues())
        .extracting(SecurityEventDTO::getEventType)
        .containsExactly(SecurityEventType.ACTIVATION_TIMEOUT, SecurityEventType.POLICY_REJECTION);
  }

  @Test
  void activePolicyDrift_emitsReadinessMismatchWithoutReplacingActivePolicy() {
    NamespaceSecurityPolicyDTO active = activePolicy(41L);
    policyStore.update(active);
    participantStatusStore.update(
        "engine-1",
        ParticipantStatusDTO.builder()
            .participantId("tenant.bank.payments.engine")
            .participantInstanceId("engine-1")
            .participantKind(ParticipantKind.ENGINE)
            .componentType("engine")
            .capabilities(ENGINE_CAPABILITIES)
            .namespace("bank.payments")
            .startedAt(clock.millis() - 100L)
            .lastSeenAt(clock.millis())
            .statusExpiresAt(clock.millis() + 500L)
            .statusVerificationLevel(StatusVerificationLevel.LOCALLY_VERIFIED_STATUS)
            .effectiveState(ParticipantEffectiveState.READY)
            .readyForDataPlane(true)
            .observedPolicyVersion(active.getActivePolicyVersion())
            .observedPolicyHash("different-hash")
            .build());
    participantStatusStore.update(
        "console-1",
        ParticipantStatusDTO.builder()
            .participantId("tenant.bank.payments.console")
            .participantInstanceId("console-1")
            .participantKind(ParticipantKind.CLIENT)
            .componentType("console")
            .capabilities(CONTROL_PLANE_CLIENT_CAPABILITIES)
            .namespace("bank.payments")
            .startedAt(clock.millis() - 100L)
            .lastSeenAt(clock.millis())
            .statusExpiresAt(clock.millis() + 500L)
            .statusVerificationLevel(StatusVerificationLevel.LOCALLY_VERIFIED_STATUS)
            .effectiveState(ParticipantEffectiveState.READY)
            .readyForDataPlane(true)
            .observedPolicyVersion(active.getActivePolicyVersion())
            .observedPolicyHash("different-hash")
            .build());

    activationService.reevaluate();
    activationService.reevaluate();

    assertThat(policyStore.get()).isEqualTo(active);
    assertThat(policyStore.getActivePolicy()).isEqualTo(active);
    ArgumentCaptor<SecurityEventDTO> captor = ArgumentCaptor.forClass(SecurityEventDTO.class);
    verify(securityEventPublisher, Mockito.times(1)).publish(captor.capture());
    assertThat(captor.getValue().getEventType()).isEqualTo(SecurityEventType.READINESS_MISMATCH);
    assertThat(captor.getValue().getMetadata())
        .containsEntry("postActivationDrift", "true")
        .containsEntry("policyMismatchParticipants", "engine-1");
  }

  @Test
  void activePolicyDrift_recoveryResetsFingerprintForFutureIncidents() {
    NamespaceSecurityPolicyDTO active = activePolicy(41L);
    policyStore.update(active);
    participantStatusStore.update(
        "engine-1",
        ParticipantStatusDTO.builder()
            .participantId("tenant.bank.payments.engine")
            .participantInstanceId("engine-1")
            .participantKind(ParticipantKind.ENGINE)
            .componentType("engine")
            .capabilities(ENGINE_CAPABILITIES)
            .namespace("bank.payments")
            .startedAt(clock.millis() - 100L)
            .lastSeenAt(clock.millis())
            .statusExpiresAt(clock.millis() + 500L)
            .statusVerificationLevel(StatusVerificationLevel.LOCALLY_VERIFIED_STATUS)
            .effectiveState(ParticipantEffectiveState.MISMATCH)
            .readyForDataPlane(false)
            .observedPolicyVersion(active.getActivePolicyVersion())
            .observedPolicyHash(active.getActivePolicyHash())
            .build());

    activationService.reevaluate();

    addReadyEnforcerParticipant(active, clock.millis() + 500L);
    activationService.reevaluate();

    participantStatusStore.update(
        "engine-1",
        ParticipantStatusDTO.builder()
            .participantId("tenant.bank.payments.engine")
            .participantInstanceId("engine-1")
            .participantKind(ParticipantKind.ENGINE)
            .componentType("engine")
            .capabilities(ENGINE_CAPABILITIES)
            .namespace("bank.payments")
            .startedAt(clock.millis() - 100L)
            .lastSeenAt(clock.millis())
            .statusExpiresAt(clock.millis() + 500L)
            .statusVerificationLevel(StatusVerificationLevel.LOCALLY_VERIFIED_STATUS)
            .effectiveState(ParticipantEffectiveState.MISMATCH)
            .readyForDataPlane(false)
            .observedPolicyVersion(active.getActivePolicyVersion())
            .observedPolicyHash(active.getActivePolicyHash())
            .build());

    activationService.reevaluate();

    verify(securityEventPublisher, Mockito.times(2)).publish(any(SecurityEventDTO.class));
  }

  @Test
  void observerCompatibilityClaim_doesNotBlockRequestedPolicyActivation() {
    NamespaceSecurityPolicyDTO requested = requestedPolicy(42L);
    addReadyEnforcerParticipant(requested, clock.millis() + 500L);
    participantStatusStore.update(
        "console-1",
        ParticipantStatusDTO.builder()
            .participantId("tenant.bank.payments.console")
            .participantInstanceId("console-1")
            .participantKind(ParticipantKind.CLIENT)
            .componentType("console")
            .capabilities(CONTROL_PLANE_CLIENT_CAPABILITIES)
            .namespace("bank.payments")
            .startedAt(clock.millis() - 100L)
            .lastSeenAt(clock.millis())
            .statusExpiresAt(clock.millis() + 500L)
            .statusVerificationLevel(StatusVerificationLevel.LOCALLY_VERIFIED_STATUS)
            .effectiveState(ParticipantEffectiveState.READY)
            .readyForDataPlane(true)
            .observedPolicyVersion(requested.getDesiredPolicyVersion())
            .observedPolicyHash("forged-hash")
            .build());

    activationService.onPolicyUpdated(requested);

    assertThat(policyStore.get()).isNotNull();
    assertThat(policyStore.get().getActivationState()).isEqualTo(SecurityActivationState.ACTIVE);
    assertThat(policyStore.getActivePolicy()).isEqualTo(policyStore.get());
    verify(securityEventPublisher, never()).publish(any(SecurityEventDTO.class));
  }

  @Test
  void protectedRuntimeClientWithoutEnforcerCapability_doesNotBlockRequestedPolicyActivation() {
    NamespaceSecurityPolicyDTO requested = requestedPolicy(42L);
    addReadyEnforcerParticipant(requested, clock.millis() + 500L);
    participantStatusStore.update(
        "runtime-client-1",
        ParticipantStatusDTO.builder()
            .participantId("tenant.bank.payments.orders-service")
            .participantInstanceId("runtime-client-1")
            .participantKind(ParticipantKind.CLIENT)
            .componentType("orders-service")
            .capabilities(PROTECTED_RUNTIME_CLIENT_CAPABILITIES)
            .namespace("bank.payments")
            .startedAt(clock.millis() - 100L)
            .lastSeenAt(clock.millis())
            .statusExpiresAt(clock.millis() + 500L)
            .statusVerificationLevel(StatusVerificationLevel.LOCALLY_VERIFIED_STATUS)
            .effectiveState(ParticipantEffectiveState.MISMATCH)
            .readyForDataPlane(false)
            .observedPolicyVersion(requested.getDesiredPolicyVersion())
            .observedPolicyHash("runtime-client-local-mismatch")
            .build());

    activationService.onPolicyUpdated(requested);

    assertThat(policyStore.get()).isNotNull();
    assertThat(policyStore.get().getActivationState()).isEqualTo(SecurityActivationState.ACTIVE);
    assertThat(policyStore.getActivePolicy()).isEqualTo(policyStore.get());
    verify(securityEventPublisher, never()).publish(any(SecurityEventDTO.class));
  }

  @Test
  void breakGlassDowngradeWithoutActorAndReason_isRejectedFailClosed() {
    NamespaceSecurityPolicyDTO previousActive =
        NamespaceSecurityPolicySupport.requireValid(
            NamespaceSecurityPolicyDTO.builder()
                .mode(SecurityMode.ANCHORED_SECURED)
                .activationState(SecurityActivationState.ACTIVE)
                .desiredPolicyVersion(41L)
                .trustAnchorRequired(true)
                .requiredSigning(RequiredSigningDTO.builder().engineOutbound(true).build())
                .activePolicyVersion(41L)
                .build());
    policyStore.update(previousActive);

    NamespaceSecurityPolicyDTO requested =
        NamespaceSecurityPolicySupport.requireValid(
            NamespaceSecurityPolicyDTO.builder()
                .mode(SecurityMode.SECURED)
                .activationState(SecurityActivationState.REQUESTED)
                .desiredPolicyVersion(42L)
                .requiredSigning(RequiredSigningDTO.builder().engineOutbound(true).build())
                .build());

    activationService.onPolicyUpdated(requested);

    assertThat(policyStore.get()).isEqualTo(previousActive);
    ArgumentCaptor<SecurityEventDTO> captor = ArgumentCaptor.forClass(SecurityEventDTO.class);
    verify(securityEventPublisher, Mockito.times(2)).publish(captor.capture());
    assertThat(captor.getAllValues())
        .extracting(SecurityEventDTO::getEventType)
        .containsExactly(
            SecurityEventType.CONTROL_PLANE_MUTATION_REJECTED, SecurityEventType.POLICY_REJECTION);
    assertThat(captor.getAllValues().getFirst().getCode())
        .isEqualTo(NamespaceSecurityPolicyActivationService.BREAK_GLASS_DOWNGRADE_REJECTED_CODE);
  }

  @Test
  void breakGlassDowngradeWithActorAndReason_isAuditedAndAllowedToActivate() {
    NamespaceSecurityPolicyDTO previousActive =
        NamespaceSecurityPolicySupport.requireValid(
            NamespaceSecurityPolicyDTO.builder()
                .mode(SecurityMode.ANCHORED_SECURED)
                .activationState(SecurityActivationState.ACTIVE)
                .desiredPolicyVersion(41L)
                .trustAnchorRequired(true)
                .requiredSigning(RequiredSigningDTO.builder().engineOutbound(true).build())
                .activePolicyVersion(41L)
                .build());
    policyStore.update(previousActive);

    NamespaceSecurityPolicyDTO requested =
        NamespaceSecurityPolicySupport.requireValid(
            NamespaceSecurityPolicyDTO.builder()
                .mode(SecurityMode.SECURED)
                .activationState(SecurityActivationState.REQUESTED)
                .desiredPolicyVersion(42L)
                .requiredSigning(RequiredSigningDTO.builder().engineOutbound(true).build())
                .breakGlassActor("ops-admin")
                .breakGlassReason("temporary trust anchor outage")
                .build());
    addReadyEnforcerParticipant(requested, clock.millis() + 500L);

    activationService.onPolicyUpdated(requested);

    assertThat(policyStore.get()).isNotNull();
    assertThat(policyStore.get().getActivationState()).isEqualTo(SecurityActivationState.ACTIVE);
    ArgumentCaptor<SecurityEventDTO> captor = ArgumentCaptor.forClass(SecurityEventDTO.class);
    verify(securityEventPublisher, Mockito.times(1)).publish(captor.capture());
    assertThat(captor.getValue().getEventType()).isEqualTo(SecurityEventType.POLICY_DOWNGRADE);
    assertThat(captor.getValue().getSeverity())
        .isEqualTo(io.taktx.dto.SecurityEventSeverity.CRITICAL);
    assertThat(captor.getValue().getMetadata())
        .containsEntry("breakGlassActor", "ops-admin")
        .containsEntry("breakGlassReason", "temporary trust anchor outage");
  }

  private void addReadyEnforcerParticipant(
      NamespaceSecurityPolicyDTO policy, long statusExpiresAt) {
    participantStatusStore.update(
        "engine-1",
        ParticipantStatusDTO.builder()
            .participantId("tenant.bank.payments.engine")
            .participantInstanceId("engine-1")
            .participantKind(ParticipantKind.ENGINE)
            .componentType("engine")
            .capabilities(ENGINE_CAPABILITIES)
            .namespace("bank.payments")
            .startedAt(clock.millis() - 100L)
            .lastSeenAt(clock.millis())
            .statusExpiresAt(statusExpiresAt)
            .statusVerificationLevel(StatusVerificationLevel.LOCALLY_VERIFIED_STATUS)
            .effectiveState(ParticipantEffectiveState.READY)
            .readyForDataPlane(true)
            .observedPolicyVersion(policy.getDesiredPolicyVersion())
            .observedPolicyHash(policy.getDesiredPolicyHash())
            .build());
  }

  private static NamespaceSecurityPolicyDTO requestedPolicy(long version) {
    return NamespaceSecurityPolicySupport.requireValid(
        NamespaceSecurityPolicyDTO.builder()
            .mode(SecurityMode.SECURED)
            .activationState(SecurityActivationState.REQUESTED)
            .desiredPolicyVersion(version)
            .requiredSigning(RequiredSigningDTO.builder().engineOutbound(true).build())
            .build());
  }

  private static NamespaceSecurityPolicyDTO anchoredRequestedPolicy(long version) {
    return NamespaceSecurityPolicySupport.requireValid(
        NamespaceSecurityPolicyDTO.builder()
            .mode(SecurityMode.ANCHORED_SECURED)
            .activationState(SecurityActivationState.REQUESTED)
            .desiredPolicyVersion(version)
            .requiredSigning(RequiredSigningDTO.builder().engineOutbound(true).build())
            .trustAnchorRequired(true)
            .build());
  }

  private static NamespaceSecurityPolicyDTO activePolicy(long version) {
    NamespaceSecurityPolicyDTO requested = requestedPolicy(version);
    return NamespaceSecurityPolicySupport.requireValid(
        NamespaceSecurityPolicyDTO.builder()
            .mode(requested.getMode())
            .activationState(SecurityActivationState.ACTIVE)
            .desiredPolicyVersion(version)
            .desiredPolicyHash(requested.getDesiredPolicyHash())
            .requiredSigning(requested.getRequiredSigning())
            .activePolicyVersion(version)
            .activePolicyHash(requested.getDesiredPolicyHash())
            .build());
  }

  private static final class MutableClock extends Clock {
    private final AtomicLong nowMs;

    private MutableClock(long initialNowMs) {
      this.nowMs = new AtomicLong(initialNowMs);
    }

    @Override
    public ZoneId getZone() {
      return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
      return this;
    }

    @Override
    public Instant instant() {
      return Instant.ofEpochMilli(nowMs.get());
    }

    @Override
    public long millis() {
      return nowMs.get();
    }

    private void advanceMillis(long deltaMs) {
      nowMs.addAndGet(deltaMs);
    }
  }
}
