/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.client;

import static org.assertj.core.api.Assertions.assertThat;

import io.taktx.dto.NamespaceSecurityPolicyDTO;
import io.taktx.dto.ParticipantCapability;
import io.taktx.dto.ParticipantKind;
import io.taktx.dto.RequiredAuthorizationDTO;
import io.taktx.dto.RequiredSigningDTO;
import io.taktx.dto.SecurityActivationState;
import io.taktx.dto.SecurityMode;
import io.taktx.dto.SecurityParticipantDescriptor;
import io.taktx.security.SigningIdentity;
import io.taktx.util.TaktPropertiesHelper;
import java.security.KeyPair;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Properties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ClientProtectedDataPlaneParticipationGuardTest {

  private TaktPropertiesHelper propertiesHelper;
  private Clock clock;

  @BeforeEach
  void setUp() {
    Properties properties = new Properties();
    properties.setProperty("bootstrap.servers", "localhost:9092");
    properties.setProperty("taktx.engine.tenant-id", "tenant");
    properties.setProperty("taktx.engine.namespace", "default");
    propertiesHelper = new TaktPropertiesHelper(properties);
    clock = Clock.fixed(Instant.parse("2026-05-24T10:15:30Z"), ZoneOffset.UTC);
  }

  @Test
  void evaluate_blocksProtectedTrafficWhilePolicyIsPendingAndNoAuthoritativePolicyExists() {
    ClientNamespaceSecurityPolicyStore store = new ClientNamespaceSecurityPolicyStore();
    store.setCurrentPolicy(requestedPolicy());

    ClientProtectedDataPlaneParticipationGuard guard =
        new ClientProtectedDataPlaneParticipationGuard(
            propertiesHelper,
            runtimeDescriptor(),
            () -> store,
            () -> null,
            () -> false,
            () -> false,
            () -> null,
            clock);

    ClientProtectedDataPlaneParticipationGuard.Decision decision =
        guard.evaluate(ProtectedClientDataPlaneOperation.START_COMMAND, null);

    assertThat(decision.permitted()).isFalse();
    assertThat(decision.reasonHint())
        .isEqualTo(ClientProtectedDataPlaneParticipationGuard.POLICY_NOT_ACTIVE_HINT);
    assertThat(decision.reasonText()).contains("becomes ACTIVE");
  }

  @Test
  void evaluate_allowsAuthorizedStartCommandWhenActivePolicyOnlyRequiresJwt() {
    ClientNamespaceSecurityPolicyStore store = new ClientNamespaceSecurityPolicyStore();
    store.setCurrentPolicy(activePolicy(false, false, true, false, false, false));

    ClientProtectedDataPlaneParticipationGuard guard =
        new ClientProtectedDataPlaneParticipationGuard(
            propertiesHelper,
            runtimeDescriptor(),
            () -> store,
            () -> null,
            () -> false,
            () -> false,
            () -> null,
            clock);

    ClientProtectedDataPlaneParticipationGuard.Decision decision =
        guard.evaluate(ProtectedClientDataPlaneOperation.START_COMMAND, "jwt-explicit");

    assertThat(decision.permitted()).isTrue();
  }

  @Test
  void evaluate_allowsExternalTaskConsumptionWhenSigningSatisfiesCompletionAuthorization() {
    ClientNamespaceSecurityPolicyStore store = new ClientNamespaceSecurityPolicyStore();
    store.setCurrentPolicy(activePolicy(false, false, false, true, true, false));

    KeyPair keyPair = io.taktx.security.SigningKeyGenerator.generate();
    SigningIdentity identity =
        SigningIdentity.ed25519(
            "worker-key",
            io.taktx.security.SigningKeyGenerator.encodePrivateKey(keyPair.getPrivate()),
            io.taktx.security.SigningKeyGenerator.encodePublicKey(keyPair.getPublic()));

    ClientProtectedDataPlaneParticipationGuard guard =
        new ClientProtectedDataPlaneParticipationGuard(
            propertiesHelper,
            runtimeDescriptor(),
            () -> store,
            () -> identity,
            () -> true,
            () -> false,
            () -> null,
            clock);

    ClientProtectedDataPlaneParticipationGuard.Decision decision =
        guard.evaluate(ProtectedClientDataPlaneOperation.EXTERNAL_TASK_CONSUME, null);

    assertThat(decision.permitted()).isTrue();
  }

  @Test
  void evaluate_blocksClientCommandsWhenPolicyRequiresSigningButNoSigningIdentityIsReady() {
    ClientNamespaceSecurityPolicyStore store = new ClientNamespaceSecurityPolicyStore();
    store.setCurrentPolicy(activePolicy(false, true, false, false, false, false));

    ClientProtectedDataPlaneParticipationGuard guard =
        new ClientProtectedDataPlaneParticipationGuard(
            propertiesHelper,
            runtimeDescriptor(),
            () -> store,
            () -> null,
            () -> false,
            () -> false,
            () -> null,
            clock);

    ClientProtectedDataPlaneParticipationGuard.Decision decision =
        guard.evaluate(ProtectedClientDataPlaneOperation.CLIENT_COMMAND, null);

    assertThat(decision.permitted()).isFalse();
    assertThat(decision.reasonHint())
        .isEqualTo(ClientProtectedDataPlaneParticipationGuard.CLIENT_COMMAND_SIGNING_UNAVAILABLE);
  }

  @Test
  void evaluate_blocksAnchoredTrafficWhenPlatformTrustAnchorIsMissing() {
    ClientNamespaceSecurityPolicyStore store = new ClientNamespaceSecurityPolicyStore();
    store.setCurrentPolicy(activePolicy(true, false, false, false, false, false));

    ClientProtectedDataPlaneParticipationGuard guard =
        new ClientProtectedDataPlaneParticipationGuard(
            propertiesHelper,
            runtimeDescriptor(),
            () -> store,
            () -> null,
            () -> false,
            () -> false,
            () -> null,
            clock);

    ClientProtectedDataPlaneParticipationGuard.Decision decision =
        guard.evaluate(ProtectedClientDataPlaneOperation.MESSAGE_EVENT, null);

    assertThat(decision.permitted()).isFalse();
    assertThat(decision.reasonHint())
        .isEqualTo(ClientProtectedDataPlaneParticipationGuard.TRUST_ANCHOR_MISSING);
  }

  @Test
  void evaluate_blocksProtectedRuntimeTrafficWhenDescriptorLacksRuntimeCapability() {
    ClientNamespaceSecurityPolicyStore store = new ClientNamespaceSecurityPolicyStore();
    store.setCurrentPolicy(activePolicy(false, false, false, false, false, false));

    ClientProtectedDataPlaneParticipationGuard guard =
        new ClientProtectedDataPlaneParticipationGuard(
            propertiesHelper,
            observerDescriptor(),
            () -> store,
            () -> null,
            () -> false,
            () -> false,
            () -> null,
            clock);

    ClientProtectedDataPlaneParticipationGuard.Decision decision =
        guard.evaluate(ProtectedClientDataPlaneOperation.MESSAGE_EVENT, null);

    assertThat(decision.permitted()).isFalse();
    assertThat(decision.reasonHint())
        .isEqualTo(ClientProtectedDataPlaneParticipationGuard.PROTECTED_RUNTIME_CAPABILITY_MISSING);
  }

  @Test
  void evaluateCurrentStatus_usesExplicitMixedCapabilityDescriptor() {
    ClientNamespaceSecurityPolicyStore store = new ClientNamespaceSecurityPolicyStore();
    NamespaceSecurityPolicyDTO policy = activePolicy(false, false, true, false, false, false);
    store.setCurrentPolicy(policy);

    SecurityParticipantDescriptor descriptor =
        new SecurityParticipantDescriptor(
            "tenant.default.admin-console",
            ParticipantKind.CLIENT,
            java.util.Set.of(
                ParticipantCapability.AUTHORITATIVE_POLICY_PUBLISHER,
                ParticipantCapability.SECURITY_OBSERVER,
                ParticipantCapability.PROTECTED_RUNTIME_PARTICIPANT),
            "admin-console");
    ClientProtectedDataPlaneParticipationGuard guard =
        new ClientProtectedDataPlaneParticipationGuard(
            propertiesHelper,
            descriptor,
            () -> store,
            () -> null,
            () -> false,
            () -> true,
            () -> null,
            clock);

    var status =
        guard.evaluateCurrentStatus(policy, ProtectedClientDataPlaneOperation.START_COMMAND, null);

    assertThat(status.getParticipantId()).isEqualTo("tenant.default.admin-console");
    assertThat(status.getParticipantKind()).isEqualTo(ParticipantKind.CLIENT);
    assertThat(status.getComponentType()).isEqualTo("admin-console");
    assertThat(status.getCapabilities())
        .containsExactlyInAnyOrderElementsOf(descriptor.capabilities());
    assertThat(status.getSupportedModes())
        .containsExactlyInAnyOrder(
            SecurityMode.OPEN, SecurityMode.SECURED, SecurityMode.ANCHORED_SECURED);
    assertThat(status.isReadyForDataPlane()).isTrue();
  }

  private SecurityParticipantDescriptor runtimeDescriptor() {
    return new SecurityParticipantDescriptor(
        "tenant.default.client",
        ParticipantKind.CLIENT,
        java.util.Set.of(ParticipantCapability.PROTECTED_RUNTIME_PARTICIPANT),
        "generic-client");
  }

  private SecurityParticipantDescriptor observerDescriptor() {
    return new SecurityParticipantDescriptor(
        "tenant.default.observer",
        ParticipantKind.CLIENT,
        java.util.Set.of(ParticipantCapability.SECURITY_OBSERVER),
        "observer");
  }

  private NamespaceSecurityPolicyDTO requestedPolicy() {
    return NamespaceSecurityPolicyDTO.builder()
        .mode(SecurityMode.SECURED)
        .activationState(SecurityActivationState.REQUESTED)
        .desiredPolicyVersion(7L)
        .desiredPolicyHash("desired-7")
        .requiredSigning(RequiredSigningDTO.builder().build())
        .requiredAuthorization(RequiredAuthorizationDTO.builder().build())
        .build();
  }

  private NamespaceSecurityPolicyDTO activePolicy(
      boolean trustAnchorRequired,
      boolean clientCommandSigning,
      boolean startCommandAuthorization,
      boolean workerResponseSigning,
      boolean externalTaskAuthorization,
      boolean userTaskAuthorization) {
    return NamespaceSecurityPolicyDTO.builder()
        .mode(trustAnchorRequired ? SecurityMode.ANCHORED_SECURED : SecurityMode.SECURED)
        .activationState(SecurityActivationState.ACTIVE)
        .desiredPolicyVersion(9L)
        .desiredPolicyHash("policy-hash-9")
        .activePolicyVersion(9L)
        .activePolicyHash("policy-hash-9")
        .trustAnchorRequired(trustAnchorRequired)
        .requiredSigning(
            RequiredSigningDTO.builder()
                .clientCommands(clientCommandSigning)
                .workerResponses(workerResponseSigning)
                .build())
        .requiredAuthorization(
            RequiredAuthorizationDTO.builder()
                .startCommands(startCommandAuthorization)
                .externalTaskCompletion(externalTaskAuthorization)
                .userTaskCompletion(userTaskAuthorization)
                .build())
        .build();
  }
}
