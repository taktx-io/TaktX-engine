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
import io.taktx.dto.SecurityMode;
import io.taktx.dto.SecurityParticipantDescriptor;
import io.taktx.security.SigningIdentity;
import io.taktx.security.SigningKeyGenerator;
import io.taktx.util.TaktPropertiesHelper;
import java.security.KeyPair;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Properties;
import java.util.Set;
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
  void evaluate_permitsOpenTrafficWhenAuthoritativePolicyIsOpen() {
    ClientNamespaceSecurityPolicyStore store = new ClientNamespaceSecurityPolicyStore();
    store.setCurrentPolicy(policy(7L, SecurityMode.OPEN, "policy-open-7"));

    ClientProtectedDataPlaneParticipationGuard guard =
        new ClientProtectedDataPlaneParticipationGuard(
            propertiesHelper,
            runtimeDescriptor(),
            () -> store,
            () -> null,
            () -> true,
            () -> false,
            () -> null,
            clock);

    ClientProtectedDataPlaneParticipationGuard.Decision decision =
        guard.evaluate(ProtectedClientDataPlaneOperation.START_COMMAND, null);

    assertThat(decision.permitted()).isTrue();
    assertThat(decision.reasonHint()).isNull();
    assertThat(decision.reasonText()).isNull();
  }

  @Test
  void evaluate_allowsAnchoredStartCommandWhenSigningAndTrustAnchorAreReady() {
    ClientNamespaceSecurityPolicyStore store = new ClientNamespaceSecurityPolicyStore();
    store.setCurrentPolicy(policy(9L, SecurityMode.ANCHORED, "policy-anchored-9"));

    SigningIdentity identity = signingIdentity("worker-key");

    ClientProtectedDataPlaneParticipationGuard guard =
        new ClientProtectedDataPlaneParticipationGuard(
            propertiesHelper,
            runtimeDescriptor(),
            () -> store,
            () -> identity,
            () -> true,
            () -> true,
            () -> "platform-public-key",
            clock);

    ClientProtectedDataPlaneParticipationGuard.Decision decision =
        guard.evaluate(ProtectedClientDataPlaneOperation.START_COMMAND, null);

    assertThat(decision.permitted()).isTrue();
  }

  @Test
  void evaluate_allowsExternalTaskConsumptionWhenAnchoredSigningIsReady() {
    ClientNamespaceSecurityPolicyStore store = new ClientNamespaceSecurityPolicyStore();
    store.setCurrentPolicy(policy(10L, SecurityMode.ANCHORED, "policy-anchored-10"));

    SigningIdentity identity = signingIdentity("worker-key");

    ClientProtectedDataPlaneParticipationGuard guard =
        new ClientProtectedDataPlaneParticipationGuard(
            propertiesHelper,
            runtimeDescriptor(),
            () -> store,
            () -> identity,
            () -> true,
            () -> true,
            () -> "platform-public-key",
            clock);

    ClientProtectedDataPlaneParticipationGuard.Decision decision =
        guard.evaluate(ProtectedClientDataPlaneOperation.EXTERNAL_TASK_CONSUME, null);

    assertThat(decision.permitted()).isTrue();
  }

  @Test
  void evaluate_blocksClientCommandsWhenAnchoredButNoSigningIdentityIsReady() {
    ClientNamespaceSecurityPolicyStore store = new ClientNamespaceSecurityPolicyStore();
    store.setCurrentPolicy(policy(11L, SecurityMode.ANCHORED, "policy-anchored-11"));

    ClientProtectedDataPlaneParticipationGuard guard =
        new ClientProtectedDataPlaneParticipationGuard(
            propertiesHelper,
            runtimeDescriptor(),
            () -> store,
            () -> null,
            () -> true,
            () -> false,
            () -> "platform-public-key",
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
    store.setCurrentPolicy(policy(12L, SecurityMode.ANCHORED, "policy-anchored-12"));

    ClientProtectedDataPlaneParticipationGuard guard =
        new ClientProtectedDataPlaneParticipationGuard(
            propertiesHelper,
            runtimeDescriptor(),
            () -> store,
            () -> null,
            () -> true,
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
    store.setCurrentPolicy(policy(13L, SecurityMode.ANCHORED, "policy-anchored-13"));

    SigningIdentity identity = signingIdentity("worker-key");

    ClientProtectedDataPlaneParticipationGuard guard =
        new ClientProtectedDataPlaneParticipationGuard(
            propertiesHelper,
            observerDescriptor(),
            () -> store,
            () -> identity,
            () -> true,
            () -> true,
            () -> "platform-public-key",
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
    NamespaceSecurityPolicyDTO policy = policy(14L, SecurityMode.ANCHORED, "policy-anchored-14");
    store.setCurrentPolicy(policy);

    SecurityParticipantDescriptor descriptor =
        new SecurityParticipantDescriptor(
            "tenant.default.admin-console",
            ParticipantKind.CLIENT,
            Set.of(
                ParticipantCapability.AUTHORITATIVE_POLICY_PUBLISHER,
                ParticipantCapability.SECURITY_OBSERVER,
                ParticipantCapability.PROTECTED_RUNTIME_PARTICIPANT),
            "admin-console");

    SigningIdentity identity = signingIdentity("worker-key");

    ClientProtectedDataPlaneParticipationGuard guard =
        new ClientProtectedDataPlaneParticipationGuard(
            propertiesHelper,
            descriptor,
            () -> store,
            () -> identity,
            () -> true,
            () -> true,
            () -> "platform-public-key",
            clock);

    var status =
        guard.evaluateCurrentStatus(policy, ProtectedClientDataPlaneOperation.START_COMMAND, null);

    assertThat(status.getParticipantId()).isEqualTo("tenant.default.admin-console");
    assertThat(status.getParticipantKind()).isEqualTo(ParticipantKind.CLIENT);
    assertThat(status.getComponentType()).isEqualTo("admin-console");
    assertThat(status.getCapabilities())
        .containsExactlyInAnyOrderElementsOf(descriptor.capabilities());
    assertThat(status.getSupportedModes())
        .containsExactlyInAnyOrder(SecurityMode.OPEN, SecurityMode.ANCHORED);
    assertThat(status.isReadyForDataPlane()).isTrue();
  }

  private SecurityParticipantDescriptor runtimeDescriptor() {
    return new SecurityParticipantDescriptor(
        "tenant.default.client",
        ParticipantKind.CLIENT,
        Set.of(ParticipantCapability.PROTECTED_RUNTIME_PARTICIPANT),
        "generic-client");
  }

  private SecurityParticipantDescriptor observerDescriptor() {
    return new SecurityParticipantDescriptor(
        "tenant.default.observer",
        ParticipantKind.CLIENT,
        Set.of(ParticipantCapability.SECURITY_OBSERVER),
        "observer");
  }

  private static NamespaceSecurityPolicyDTO policy(
      long version, SecurityMode mode, String policyHash) {
    return NamespaceSecurityPolicyDTO.builder()
        .mode(mode)
        .policyVersion(version)
        .policyHash(policyHash)
        .build();
  }

  private static SigningIdentity signingIdentity(String keyId) {
    KeyPair keyPair = SigningKeyGenerator.generate();
    return SigningIdentity.ed25519(
        keyId,
        SigningKeyGenerator.encodePrivateKey(keyPair.getPrivate()),
        SigningKeyGenerator.encodePublicKey(keyPair.getPublic()));
  }
}
