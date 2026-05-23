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

import io.taktx.dto.NamespaceSecurityPolicyDTO;
import io.taktx.dto.RequiredAuthorizationDTO;
import io.taktx.dto.RequiredSigningDTO;
import io.taktx.dto.SecurityActivationState;
import io.taktx.dto.SecurityMode;
import org.junit.jupiter.api.Test;

class NamespaceSecurityPolicySupportTest {

  @Test
  void normalize_fillsLegacyAliasesAndComputesDesiredHash() {
    NamespaceSecurityPolicyDTO policy =
        NamespaceSecurityPolicyDTO.builder()
            .mode(SecurityMode.COMMUNITY_SECURED)
            .activationState(SecurityActivationState.REQUESTED)
            .policyVersion(42L)
            .requiredSigning(RequiredSigningDTO.builder().engineOutbound(true).build())
            .requiredAuthorization(RequiredAuthorizationDTO.builder().startCommands(true).build())
            .build();

    NamespaceSecurityPolicyDTO normalized = NamespaceSecurityPolicySupport.normalize(policy);

    assertThat(normalized.getDesiredPolicyVersion()).isEqualTo(42L);
    assertThat(normalized.getPolicyVersion()).isEqualTo(42L);
    assertThat(normalized.getDesiredPolicyHash()).isNotBlank();
    assertThat(normalized.getPolicyHash()).isEqualTo(normalized.getDesiredPolicyHash());
  }

  @Test
  void canonicalHash_ignoresDesiredAndActiveIdentityWrappers() {
    NamespaceSecurityPolicyDTO baseline =
        NamespaceSecurityPolicyDTO.builder()
            .mode(SecurityMode.ANCHORED_SECURED)
            .activationState(SecurityActivationState.REQUESTED)
            .desiredPolicyVersion(5L)
            .desiredPolicyHash("requested-hash")
            .activePolicyVersion(4L)
            .activePolicyHash("active-hash")
            .requiredSigning(
                RequiredSigningDTO.builder()
                    .engineOutbound(true)
                    .clientCommands(true)
                    .workerResponses(true)
                    .build())
            .requiredAuthorization(
                RequiredAuthorizationDTO.builder()
                    .startCommands(true)
                    .externalTaskCompletion(true)
                    .userTaskCompletion(true)
                    .build())
            .trustAnchorRequired(true)
            .policyVersion(5L)
            .policyHash("legacy-hash")
            .build();

    NamespaceSecurityPolicyDTO sameEffectiveContentDifferentWrapper =
        baseline.toBuilder()
            .desiredPolicyVersion(999L)
            .desiredPolicyHash("other-requested-hash")
            .activePolicyVersion(123L)
            .activePolicyHash("other-active-hash")
            .policyVersion(999L)
            .policyHash("other-legacy-hash")
            .build();

    assertThat(NamespaceSecurityPolicySupport.canonicalHash(baseline))
        .isEqualTo(
            NamespaceSecurityPolicySupport.canonicalHash(sameEffectiveContentDifferentWrapper));
  }

  @Test
  void validationErrors_rejectAnchoredModeWithoutTrustAnchorRequirement() {
    NamespaceSecurityPolicyDTO policy =
        NamespaceSecurityPolicyDTO.builder()
            .mode(SecurityMode.ANCHORED_SECURED)
            .activationState(SecurityActivationState.REQUESTED)
            .desiredPolicyVersion(1L)
            .build();

    assertThat(NamespaceSecurityPolicySupport.validationErrors(policy))
        .contains("ANCHORED_SECURED requires trustAnchorRequired=true");
  }

  @Test
  void requireValid_rejectsActiveIdentityMismatch() {
    NamespaceSecurityPolicyDTO policy =
        NamespaceSecurityPolicyDTO.builder()
            .mode(SecurityMode.COMMUNITY_SECURED)
            .activationState(SecurityActivationState.ACTIVE)
            .desiredPolicyVersion(10L)
            .desiredPolicyHash("requested")
            .activePolicyVersion(9L)
            .activePolicyHash("active")
            .build();

    assertThatThrownBy(() -> NamespaceSecurityPolicySupport.requireValid(policy))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining(
            "ACTIVE policy requires desiredPolicyVersion to match activePolicyVersion")
        .hasMessageContaining("ACTIVE policy requires desiredPolicyHash to match activePolicyHash");
  }

  @Test
  void requireValid_acceptsConsistentActivePolicy() {
    NamespaceSecurityPolicyDTO policy =
        NamespaceSecurityPolicyDTO.builder()
            .mode(SecurityMode.COMMUNITY_SECURED)
            .activationState(SecurityActivationState.ACTIVE)
            .desiredPolicyVersion(10L)
            .requiredSigning(RequiredSigningDTO.builder().engineOutbound(true).build())
            .requiredAuthorization(RequiredAuthorizationDTO.builder().startCommands(true).build())
            .activePolicyVersion(10L)
            .build();

    NamespaceSecurityPolicyDTO validated = NamespaceSecurityPolicySupport.requireValid(policy);

    assertThat(validated.getDesiredPolicyHash()).isNotBlank();
    assertThat(validated.getActivePolicyHash()).isEqualTo(validated.getDesiredPolicyHash());
  }

  @Test
  void requireValid_rejectsPartialBreakGlassMetadata() {
    NamespaceSecurityPolicyDTO policy =
        NamespaceSecurityPolicyDTO.builder()
            .mode(SecurityMode.COMMUNITY_SECURED)
            .activationState(SecurityActivationState.REQUESTED)
            .desiredPolicyVersion(10L)
            .breakGlassActor("ops-admin")
            .build();

    assertThatThrownBy(() -> NamespaceSecurityPolicySupport.requireValid(policy))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("breakGlassActor and breakGlassReason must be provided together");
  }

  @Test
  void parseSecurityMode_acceptsCommonOperatorFormats() {
    assertThat(NamespaceSecurityPolicySupport.parseSecurityMode("community-secured"))
        .isEqualTo(SecurityMode.COMMUNITY_SECURED);
    assertThat(NamespaceSecurityPolicySupport.parseSecurityMode("anchored secured"))
        .isEqualTo(SecurityMode.ANCHORED_SECURED);
    assertThat(NamespaceSecurityPolicySupport.parseSecurityMode("MISCONFIGURED_SECURITY"))
        .isEqualTo(SecurityMode.MISCONFIGURED_SECURITY);
  }

  @Test
  void parseRequiredSigning_parsesCommaSeparatedTokens() {
    RequiredSigningDTO parsed =
        NamespaceSecurityPolicySupport.parseRequiredSigning(
            "engine-outbound, client_commands, worker responses");

    assertThat(parsed.isEngineOutbound()).isTrue();
    assertThat(parsed.isClientCommands()).isTrue();
    assertThat(parsed.isWorkerResponses()).isTrue();
  }

  @Test
  void parseRequiredAuthorization_parsesTokenSet() {
    RequiredAuthorizationDTO parsed =
        NamespaceSecurityPolicySupport.parseRequiredAuthorization(
            java.util.Set.of("start-commands", "external_task_completion"));

    assertThat(parsed.isStartCommands()).isTrue();
    assertThat(parsed.isExternalTaskCompletion()).isTrue();
    assertThat(parsed.isUserTaskCompletion()).isFalse();
  }

  @Test
  void parseHelpers_rejectUnsupportedTokens() {
    assertThatThrownBy(() -> NamespaceSecurityPolicySupport.parseSecurityMode("unknown-mode"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Unsupported security mode");
    assertThatThrownBy(() -> NamespaceSecurityPolicySupport.parseRequiredSigning("bogus"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Unsupported signing requirement");
    assertThatThrownBy(() -> NamespaceSecurityPolicySupport.parseRequiredAuthorization("bogus"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Unsupported authorization requirement");
  }
}
