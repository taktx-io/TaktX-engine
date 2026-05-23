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
import io.taktx.dto.ParticipantEffectiveState;
import io.taktx.dto.ParticipantRole;
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
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

class NamespaceSecurityPolicyActivationServiceTest {

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
    addReadyParticipant(ParticipantRole.ENGINE, requested, clock.millis() + 500L);
    addReadyParticipant(ParticipantRole.INGESTER, requested, clock.millis() + 500L);
    addReadyParticipant(ParticipantRole.CONSOLE, requested, clock.millis() + 500L);

    activationService.onPolicyUpdated(requested);

    assertThat(policyStore.get()).isNotNull();
    assertThat(policyStore.get().getActivationState()).isEqualTo(SecurityActivationState.ACTIVE);
    assertThat(policyStore.get().getActivePolicyVersion()).isEqualTo(42L);
    assertThat(policyStore.get().getActivePolicyHash()).isEqualTo(policyStore.get().getDesiredPolicyHash());
    assertThat(policyStore.getActivePolicy()).isEqualTo(policyStore.get());
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
            .role(ParticipantRole.ENGINE)
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
    addReadyParticipant(ParticipantRole.INGESTER, requested, clock.millis() + 500L);
    addReadyParticipant(ParticipantRole.CONSOLE, requested, clock.millis() + 500L);

    activationService.onPolicyUpdated(requested);

    assertThat(policyStore.get()).isNotNull();
    assertThat(policyStore.get().getActivationState()).isEqualTo(SecurityActivationState.VALIDATING);
    assertThat(policyStore.getValidationStartedAtMs()).isEqualTo(clock.millis());
    verify(securityEventPublisher, never()).publish(any(SecurityEventDTO.class));
  }

  @Test
  void timeout_rollsBackToPreviousActivePolicyAndPublishesTimeoutAndRejectionEvents() {
    NamespaceSecurityPolicyDTO previousActive = activePolicy(41L);
    policyStore.update(previousActive);

    NamespaceSecurityPolicyDTO requested = requestedPolicy(42L);
    activationService.onPolicyUpdated(requested);

    assertThat(policyStore.get().getActivationState()).isEqualTo(SecurityActivationState.VALIDATING);

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
    addReadyParticipant(ParticipantRole.ENGINE, requested, clock.millis() + 500L);
    addReadyParticipant(ParticipantRole.INGESTER, requested, clock.millis() + 500L);
    participantStatusStore.update(
        "console-1",
        ParticipantStatusDTO.builder()
            .participantId("tenant.bank.payments.console")
            .participantInstanceId("console-1")
            .role(ParticipantRole.CONSOLE)
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
        .containsEntry("policyMismatchParticipants", "console-1");
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

  private void addReadyParticipant(
      ParticipantRole role, NamespaceSecurityPolicyDTO policy, long statusExpiresAt) {
    String instanceId = role.name().toLowerCase() + "-1";
    participantStatusStore.update(
        instanceId,
        ParticipantStatusDTO.builder()
            .participantId("tenant.bank.payments." + role.name().toLowerCase())
            .participantInstanceId(instanceId)
            .role(role)
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
            .mode(SecurityMode.COMMUNITY_SECURED)
            .activationState(SecurityActivationState.REQUESTED)
            .desiredPolicyVersion(version)
            .requiredSigning(RequiredSigningDTO.builder().engineOutbound(true).build())
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




