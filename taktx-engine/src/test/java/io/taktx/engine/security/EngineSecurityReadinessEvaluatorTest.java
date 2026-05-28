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
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class EngineSecurityReadinessEvaluatorTest {

  private TaktConfiguration configuration;
  private NamespaceSecurityPolicyStore policyStore;
  private MessageSigningService messageSigningService;
  private Clock clock;

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
  }

  @Test
  void noExplicitPolicy_defaultsToReadyCommunityOpenStatus() {
    EngineSecurityReadinessEvaluator evaluator =
        new EngineSecurityReadinessEvaluator(
            configuration, policyStore, messageSigningService, clock);

    var status = evaluator.evaluateCurrentStatus();

    assertThat(status.getEffectiveState()).isEqualTo(io.taktx.dto.ParticipantEffectiveState.READY);
    assertThat(status.isReadyForDataPlane()).isTrue();
    assertThat(status.getObservedPolicyVersion()).isNull();
    assertThat(status.getMismatchReasons()).isEmpty();
  }

  @Test
  void validatingPolicy_withoutPreviousActivePolicy_preservesCommunityOpenReadiness() {
    policyStore.update(
        NamespaceSecurityPolicyDTO.builder()
            .mode(SecurityMode.SECURED)
            .activationState(SecurityActivationState.VALIDATING)
            .desiredPolicyVersion(42L)
            .build());

    EngineSecurityReadinessEvaluator evaluator =
        new EngineSecurityReadinessEvaluator(
            configuration, policyStore, messageSigningService, clock);

    var status = evaluator.evaluateCurrentStatus();

    assertThat(status.getEffectiveState()).isEqualTo(io.taktx.dto.ParticipantEffectiveState.READY);
    assertThat(status.isReadyForDataPlane()).isTrue();
    assertThat(status.getObservedPolicyVersion()).isNull();
    assertThat(status.getObservedPolicyHash()).isNull();
    assertThat(status.getMismatchReasons()).isEmpty();
  }

  @Test
  void validatingPolicy_withPreviousActivePolicy_evaluatesAgainstPreviousActiveIdentity() {
    NamespaceSecurityPolicyDTO previousActive =
        NamespaceSecurityPolicyDTO.builder()
            .mode(SecurityMode.SECURED)
            .activationState(SecurityActivationState.ACTIVE)
            .desiredPolicyVersion(41L)
            .requiredSigning(RequiredSigningDTO.builder().engineOutbound(true).build())
            .activePolicyVersion(41L)
            .build();
    policyStore.update(previousActive);
    policyStore.setCurrentPolicy(
        NamespaceSecurityPolicyDTO.builder()
            .mode(SecurityMode.ANCHORED_SECURED)
            .activationState(SecurityActivationState.VALIDATING)
            .desiredPolicyVersion(42L)
            .requiredSigning(RequiredSigningDTO.builder().engineOutbound(true).build())
            .trustAnchorRequired(true)
            .activePolicyVersion(41L)
            .activePolicyHash(policyStore.getActivePolicy().getActivePolicyHash())
            .build());

    EngineSecurityReadinessEvaluator evaluator =
        new EngineSecurityReadinessEvaluator(
            configuration, policyStore, messageSigningService, clock);

    var status = evaluator.evaluateCurrentStatus();

    assertThat(status.getEffectiveState()).isEqualTo(io.taktx.dto.ParticipantEffectiveState.READY);
    assertThat(status.isReadyForDataPlane()).isTrue();
    assertThat(status.getObservedPolicyVersion()).isEqualTo(41L);
    assertThat(status.getObservedPolicyHash())
        .isEqualTo(policyStore.getActivePolicy().getActivePolicyHash());
    assertThat(status.getMismatchReasons()).isEmpty();
  }

  @Test
  void anchoredActivePolicy_withoutTrustAnchor_reportsMismatch() {
    policyStore.update(
        NamespaceSecurityPolicyDTO.builder()
            .mode(SecurityMode.ANCHORED_SECURED)
            .activationState(SecurityActivationState.ACTIVE)
            .desiredPolicyVersion(42L)
            .requiredSigning(RequiredSigningDTO.builder().engineOutbound(true).build())
            .trustAnchorRequired(true)
            .activePolicyVersion(42L)
            .build());

    EngineSecurityReadinessEvaluator evaluator =
        new EngineSecurityReadinessEvaluator(
            configuration, policyStore, messageSigningService, clock);

    var status = evaluator.evaluateCurrentStatus();

    assertThat(status.getEffectiveState())
        .isEqualTo(io.taktx.dto.ParticipantEffectiveState.MISMATCH);
    assertThat(status.isReadyForDataPlane()).isFalse();
    assertThat(status.getMismatchReasons())
        .extracting(io.taktx.dto.PolicyMismatchReasonDTO::getCode)
        .contains(EngineSecurityReadinessEvaluator.TRUST_ANCHOR_MISSING);
  }

  @Test
  void anchoredActivePolicy_withTrustAnchorAndSigningAvailable_reportsReady() {
    when(configuration.getPlatformPublicKey()).thenReturn("platform-public-key");

    policyStore.update(
        NamespaceSecurityPolicyDTO.builder()
            .mode(SecurityMode.ANCHORED_SECURED)
            .activationState(SecurityActivationState.ACTIVE)
            .desiredPolicyVersion(42L)
            .requiredSigning(RequiredSigningDTO.builder().engineOutbound(true).build())
            .trustAnchorRequired(true)
            .activePolicyVersion(42L)
            .build());

    EngineSecurityReadinessEvaluator evaluator =
        new EngineSecurityReadinessEvaluator(
            configuration, policyStore, messageSigningService, clock);

    var status = evaluator.evaluateCurrentStatus();

    assertThat(status.getEffectiveState()).isEqualTo(io.taktx.dto.ParticipantEffectiveState.READY);
    assertThat(status.isReadyForDataPlane()).isTrue();
    assertThat(status.getObservedPolicyVersion()).isEqualTo(42L);
    assertThat(status.getObservedPolicyHash())
        .isEqualTo(policyStore.getActivePolicy().getActivePolicyHash());
    assertThat(status.getMismatchReasons()).isEmpty();
  }

  @Test
  void activePolicy_requiringEngineOutboundSigning_detectsUnpublishedKey() {
    when(messageSigningService.getKeyId()).thenReturn(null);
    when(messageSigningService.isPublicKeyPublished()).thenReturn(false);

    policyStore.update(
        NamespaceSecurityPolicyDTO.builder()
            .mode(SecurityMode.SECURED)
            .activationState(SecurityActivationState.ACTIVE)
            .desiredPolicyVersion(42L)
            .requiredSigning(RequiredSigningDTO.builder().engineOutbound(true).build())
            .activePolicyVersion(42L)
            .build());

    EngineSecurityReadinessEvaluator evaluator =
        new EngineSecurityReadinessEvaluator(
            configuration, policyStore, messageSigningService, clock);

    var status = evaluator.evaluateCurrentStatus();

    assertThat(status.getEffectiveState())
        .isEqualTo(io.taktx.dto.ParticipantEffectiveState.MISMATCH);
    assertThat(status.getMismatchReasons())
        .extracting(io.taktx.dto.PolicyMismatchReasonDTO::getCode)
        .contains(EngineSecurityReadinessEvaluator.ENGINE_SIGNING_UNAVAILABLE);
  }

  @Test
  void activePolicy_statusIncludesAuthoritativeObservedIdentityAndTtlFields() {
    policyStore.update(
        NamespaceSecurityPolicyDTO.builder()
            .mode(SecurityMode.SECURED)
            .activationState(SecurityActivationState.ACTIVE)
            .desiredPolicyVersion(42L)
            .requiredSigning(RequiredSigningDTO.builder().engineOutbound(true).build())
            .activePolicyVersion(42L)
            .build());

    EngineSecurityReadinessEvaluator evaluator =
        new EngineSecurityReadinessEvaluator(
            configuration, policyStore, messageSigningService, clock);

    var status = evaluator.evaluateCurrentStatus();

    assertThat(status.getObservedPolicyVersion()).isEqualTo(42L);
    assertThat(status.getObservedPolicyHash())
        .isEqualTo(policyStore.getActivePolicy().getActivePolicyHash());
    assertThat(status.getLastSeenAt()).isEqualTo(1_716_450_000_000L);
    assertThat(status.getStatusExpiresAt())
        .isEqualTo(1_716_450_000_000L + EngineSecurityReadinessEvaluator.STATUS_TTL_MS);
    assertThat(status.getParticipantInstanceId()).contains("tenant.bank.payments@");
  }

  @Test
  void mismatchReasons_includeHumanReadableMessages() {
    policyStore.update(
        NamespaceSecurityPolicyDTO.builder()
            .mode(SecurityMode.ANCHORED_SECURED)
            .activationState(SecurityActivationState.ACTIVE)
            .desiredPolicyVersion(42L)
            .requiredSigning(RequiredSigningDTO.builder().engineOutbound(true).build())
            .trustAnchorRequired(true)
            .activePolicyVersion(42L)
            .build());

    EngineSecurityReadinessEvaluator evaluator =
        new EngineSecurityReadinessEvaluator(
            configuration, policyStore, messageSigningService, clock);

    var status = evaluator.evaluateCurrentStatus();

    assertThat(status.getMismatchReasons())
        .allSatisfy(
            reason -> {
              assertThat(reason.getCode()).isNotBlank();
              assertThat(reason.getMessage()).isNotBlank();
            });
  }
}
