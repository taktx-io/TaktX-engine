/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.engine.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import io.taktx.dto.NamespaceSecurityPolicyDTO;
import io.taktx.dto.RequiredSigningDTO;
import io.taktx.dto.SecurityActivationState;
import io.taktx.dto.SecurityMode;
import io.taktx.engine.config.NamespaceSecurityPolicyStore;
import io.taktx.engine.config.TaktConfiguration;
import io.taktx.security.NamespaceSecurityPolicySupport;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class ProtectedDataPlaneParticipationGuardTest {

  private TaktConfiguration configuration;
  private NamespaceSecurityPolicyStore policyStore;
  private MessageSigningService messageSigningService;
  private Clock clock;
  private ProtectedDataPlaneParticipationGuard guard;

  @BeforeEach
  void setUp() {
    configuration = Mockito.mock(TaktConfiguration.class);
    when(configuration.getTenantId()).thenReturn("tenant");
    when(configuration.getNamespace()).thenReturn("bank.payments");
    when(configuration.getHost()).thenReturn("engine-host");
    when(configuration.getPort()).thenReturn(8080);
    when(configuration.getPlatformPublicKey()).thenReturn(null);

    policyStore = new NamespaceSecurityPolicyStore();
    messageSigningService = Mockito.mock(MessageSigningService.class);
    when(messageSigningService.getKeyId()).thenReturn("engine-key-1");
    when(messageSigningService.isPublicKeyPublished()).thenReturn(true);

    clock = Clock.fixed(Instant.ofEpochMilli(1_716_450_000_000L), ZoneOffset.UTC);
    guard =
        new ProtectedDataPlaneParticipationGuard(
            policyStore,
            new EngineSecurityReadinessEvaluator(
                configuration, policyStore, messageSigningService, clock),
            clock);
  }

  @Test
  void noExplicitPolicy_allowsDefaultCommunityOpenParticipation() {
    ProtectedDataPlaneParticipationGuard.Decision decision = guard.evaluate();

    assertThat(decision.permitted()).isTrue();
    assertThat(decision.reasonHint()).isNull();
  }

  @Test
  void requestedPolicyWithoutAuthoritativeActivePolicy_blocksProtectedParticipation() {
    policyStore.setCurrentPolicy(requestedPolicy(42L));

    ProtectedDataPlaneParticipationGuard.Decision decision = guard.evaluate();

    assertThat(decision.permitted()).isFalse();
    assertThat(decision.reasonHint())
        .isEqualTo(ProtectedDataPlaneParticipationGuard.POLICY_NOT_ACTIVE_HINT);
    assertThat(decision.reasonText()).contains("becomes ACTIVE");
  }

  @Test
  void activeAnchoredPolicyWithoutTrustAnchor_blocksProtectedParticipation() {
    policyStore.update(anchoredActivePolicy(42L));

    ProtectedDataPlaneParticipationGuard.Decision decision = guard.evaluate();

    assertThat(decision.permitted()).isFalse();
    assertThat(decision.reasonHint()).isEqualTo(EngineSecurityReadinessEvaluator.TRUST_ANCHOR_MISSING);
    assertThat(decision.reasonText()).contains("no platform public key");
  }

  @Test
  void pendingPolicyWithPreviousActivePolicy_allowsParticipationUnderPreviousActiveIdentity() {
    NamespaceSecurityPolicyDTO previousActive = activePolicy(41L);
    policyStore.update(previousActive);
    policyStore.setCurrentPolicy(
        requestedPolicy(42L).toBuilder()
            .activationState(SecurityActivationState.VALIDATING)
            .activePolicyVersion(previousActive.getActivePolicyVersion())
            .activePolicyHash(previousActive.getActivePolicyHash())
            .build());

    ProtectedDataPlaneParticipationGuard.Decision decision = guard.evaluate();

    assertThat(decision.permitted()).isTrue();
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

  private static NamespaceSecurityPolicyDTO anchoredActivePolicy(long version) {
    NamespaceSecurityPolicyDTO requested =
        NamespaceSecurityPolicySupport.requireValid(
            NamespaceSecurityPolicyDTO.builder()
                .mode(SecurityMode.ANCHORED_SECURED)
                .activationState(SecurityActivationState.REQUESTED)
                .desiredPolicyVersion(version)
                .requiredSigning(RequiredSigningDTO.builder().engineOutbound(true).build())
                .trustAnchorRequired(true)
                .build());
    return NamespaceSecurityPolicySupport.requireValid(
        requested.toBuilder()
            .activationState(SecurityActivationState.ACTIVE)
            .activePolicyVersion(version)
            .activePolicyHash(requested.getDesiredPolicyHash())
            .build());
  }
}


