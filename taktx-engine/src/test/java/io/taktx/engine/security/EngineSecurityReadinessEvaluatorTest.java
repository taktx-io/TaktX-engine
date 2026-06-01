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
    when(configuration.getSigningIdentitySourceType()).thenReturn("generated");
    when(configuration.getPlatformPublicKey()).thenReturn(null);
    when(configuration.getEngineKeyRegistrationSignature()).thenReturn(null);

    policyStore = new NamespaceSecurityPolicyStore();
    messageSigningService = Mockito.mock(MessageSigningService.class);
    when(messageSigningService.getKeyId()).thenReturn("engine-key-1");
    when(messageSigningService.getPublicKeyBase64()).thenReturn("engine-public-key-1");
    when(messageSigningService.isPublicKeyPublished()).thenReturn(true);
    when(messageSigningService.hasPublishableSigningIdentity()).thenReturn(false);
    when(messageSigningService.hasLegacyProtectedRuntimeRequirement()).thenReturn(false);

    clock = Clock.fixed(Instant.ofEpochMilli(1_716_450_000_000L), ZoneOffset.UTC);
  }

  @Test
  void noExplicitPolicy_defaultsToReadyOpenStatus() {
    EngineSecurityReadinessEvaluator evaluator =
        new EngineSecurityReadinessEvaluator(
            configuration, policyStore, messageSigningService, clock);

    var status = evaluator.evaluateCurrentStatus();

    assertThat(status.getEffectiveState()).isEqualTo(io.taktx.dto.ParticipantEffectiveState.READY);
    assertThat(status.isReadyForDataPlane()).isTrue();
    assertThat(status.getObservedPolicyVersion()).isNull();
    assertThat(status.getSupportedModes()).containsExactly(SecurityMode.OPEN);
    assertThat(status.getMismatchReasons()).isEmpty();
  }

  @Test
  void stableAnchoredRuntime_reportsAnchoredAsSupportedMode() {
    when(configuration.getSigningIdentitySourceType()).thenReturn("file");
    when(configuration.getPlatformPublicKey()).thenReturn("platform-public-key");
    when(configuration.getEngineKeyRegistrationSignature()).thenReturn("engine-registration-signature");
    when(messageSigningService.hasPublishableSigningIdentity()).thenReturn(true);

    EngineSecurityReadinessEvaluator evaluator =
        new EngineSecurityReadinessEvaluator(
            configuration, policyStore, messageSigningService, clock);

    var status = evaluator.evaluateCurrentStatus();

    assertThat(status.getSupportedModes())
        .containsExactlyInAnyOrder(SecurityMode.OPEN, SecurityMode.ANCHORED);
  }

  @Test
  void anchoredPolicy_withoutTrustAnchor_reportsMismatch() {
    when(configuration.getSigningIdentitySourceType()).thenReturn("file");
    when(messageSigningService.hasPublishableSigningIdentity()).thenReturn(true);
    policyStore.update(policy(SecurityMode.ANCHORED, 42L));

    EngineSecurityReadinessEvaluator evaluator =
        new EngineSecurityReadinessEvaluator(
            configuration, policyStore, messageSigningService, clock);

    var status = evaluator.evaluateCurrentStatus();

    assertThat(status.getEffectiveState())
        .isEqualTo(io.taktx.dto.ParticipantEffectiveState.MISMATCH);
    assertThat(status.isReadyForDataPlane()).isFalse();
    assertThat(status.getObservedPolicyVersion()).isEqualTo(42L);
    assertThat(status.getObservedPolicyHash()).isEqualTo(policy(SecurityMode.ANCHORED, 42L).getPolicyHash());
    assertThat(status.getMismatchReasons())
        .extracting(io.taktx.dto.PolicyMismatchReasonDTO::getCode)
        .contains(EngineSecurityReadinessEvaluator.TRUST_ANCHOR_MISSING);
  }

  @Test
  void anchoredPolicy_withoutStableSigningSource_reportsMismatch() {
    when(configuration.getSigningIdentitySourceType()).thenReturn("generated");
    when(configuration.getPlatformPublicKey()).thenReturn("platform-public-key");
    when(configuration.getEngineKeyRegistrationSignature()).thenReturn("engine-registration-signature");
    when(messageSigningService.hasPublishableSigningIdentity()).thenReturn(true);
    policyStore.update(policy(SecurityMode.ANCHORED, 42L));

    EngineSecurityReadinessEvaluator evaluator =
        new EngineSecurityReadinessEvaluator(
            configuration, policyStore, messageSigningService, clock);

    var status = evaluator.evaluateCurrentStatus();

    assertThat(status.getEffectiveState())
        .isEqualTo(io.taktx.dto.ParticipantEffectiveState.MISMATCH);
    assertThat(status.getMismatchReasons())
        .extracting(io.taktx.dto.PolicyMismatchReasonDTO::getCode)
        .contains(EngineSecurityReadinessEvaluator.STABLE_SIGNING_SOURCE_REQUIRED);
  }

  @Test
  void anchoredPolicy_withTrustAnchorButUnpublishedKey_reportsSigningUnavailable() {
    when(configuration.getSigningIdentitySourceType()).thenReturn("file");
    when(configuration.getPlatformPublicKey()).thenReturn("platform-public-key");
    when(configuration.getEngineKeyRegistrationSignature()).thenReturn("engine-registration-signature");
    when(messageSigningService.hasPublishableSigningIdentity()).thenReturn(true);
    when(messageSigningService.getKeyId()).thenReturn(null);
    when(messageSigningService.isPublicKeyPublished()).thenReturn(false);
    policyStore.update(policy(SecurityMode.ANCHORED, 42L));

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
  void anchoredPolicy_withTrustAnchorAndSigningAvailable_reportsReady() {
    when(configuration.getSigningIdentitySourceType()).thenReturn("file");
    when(configuration.getPlatformPublicKey()).thenReturn("platform-public-key");
    when(configuration.getEngineKeyRegistrationSignature()).thenReturn("engine-registration-signature");
    when(messageSigningService.hasPublishableSigningIdentity()).thenReturn(true);
    policyStore.update(policy(SecurityMode.ANCHORED, 42L));

    EngineSecurityReadinessEvaluator evaluator =
        new EngineSecurityReadinessEvaluator(
            configuration, policyStore, messageSigningService, clock);

    var status = evaluator.evaluateCurrentStatus();

    assertThat(status.getEffectiveState()).isEqualTo(io.taktx.dto.ParticipantEffectiveState.READY);
    assertThat(status.isReadyForDataPlane()).isTrue();
    assertThat(status.getObservedPolicyVersion()).isEqualTo(42L);
    assertThat(status.getObservedPolicyHash()).isEqualTo(policy(SecurityMode.ANCHORED, 42L).getPolicyHash());
    assertThat(status.getSupportedModes())
        .containsExactlyInAnyOrder(SecurityMode.OPEN, SecurityMode.ANCHORED);
    assertThat(status.getMismatchReasons()).isEmpty();
  }

  private static NamespaceSecurityPolicyDTO policy(SecurityMode mode, long version) {
    return io.taktx.security.NamespaceSecurityPolicySupport.requireValid(
        NamespaceSecurityPolicyDTO.builder().mode(mode).policyVersion(version).build());
  }
}
