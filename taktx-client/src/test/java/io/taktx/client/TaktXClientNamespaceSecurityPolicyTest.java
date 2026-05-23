/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.taktx.dto.NamespaceSecurityPolicyDTO;
import io.taktx.dto.RequiredAuthorizationDTO;
import io.taktx.dto.RequiredSigningDTO;
import io.taktx.dto.SecurityActivationState;
import io.taktx.dto.SecurityMode;
import org.junit.jupiter.api.Test;

class TaktXClientNamespaceSecurityPolicyTest {

  @Test
  void normalizeNamespaceSecurityPolicy_fillsAliasesAndCanonicalHash() {
    NamespaceSecurityPolicyDTO normalized =
        TaktXClient.normalizeNamespaceSecurityPolicy(
            NamespaceSecurityPolicyDTO.builder()
                .mode(SecurityMode.COMMUNITY_SECURED)
                .activationState(SecurityActivationState.REQUESTED)
                .policyVersion(42L)
                .requiredSigning(RequiredSigningDTO.builder().engineOutbound(true).build())
                .requiredAuthorization(
                    RequiredAuthorizationDTO.builder().startCommands(true).build())
                .build());

    assertThat(normalized.getDesiredPolicyVersion()).isEqualTo(42L);
    assertThat(normalized.getPolicyVersion()).isEqualTo(42L);
    assertThat(normalized.getDesiredPolicyHash()).isNotBlank();
    assertThat(normalized.getPolicyHash()).isEqualTo(normalized.getDesiredPolicyHash());
  }

  @Test
  void canonicalNamespaceSecurityPolicyHash_ignoresIdentityWrapperFields() {
    NamespaceSecurityPolicyDTO baseline =
        NamespaceSecurityPolicyDTO.builder()
            .mode(SecurityMode.ANCHORED_SECURED)
            .activationState(SecurityActivationState.REQUESTED)
            .desiredPolicyVersion(1L)
            .desiredPolicyHash("hash-a")
            .activePolicyVersion(7L)
            .activePolicyHash("hash-b")
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
            .policyVersion(1L)
            .policyHash("legacy-a")
            .build();

    NamespaceSecurityPolicyDTO sameEffectivePolicy =
        baseline.toBuilder()
            .desiredPolicyVersion(2L)
            .desiredPolicyHash("hash-c")
            .activePolicyVersion(9L)
            .activePolicyHash("hash-d")
            .policyVersion(2L)
            .policyHash("legacy-b")
            .build();

    assertThat(TaktXClient.canonicalNamespaceSecurityPolicyHash(baseline))
        .isEqualTo(TaktXClient.canonicalNamespaceSecurityPolicyHash(sameEffectivePolicy));
  }

  @Test
  void validateNamespaceSecurityPolicy_rejectsInvalidAnchoredPolicy() {
    NamespaceSecurityPolicyDTO policy =
        NamespaceSecurityPolicyDTO.builder()
            .mode(SecurityMode.ANCHORED_SECURED)
            .activationState(SecurityActivationState.REQUESTED)
            .desiredPolicyVersion(1L)
            .build();

    assertThatThrownBy(() -> TaktXClient.validateNamespaceSecurityPolicy(policy))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("ANCHORED_SECURED requires trustAnchorRequired=true");
  }

  @Test
  void validateNamespaceSecurityPolicy_returnsNormalizedPolicyForValidInput() {
    NamespaceSecurityPolicyDTO validated =
        TaktXClient.validateNamespaceSecurityPolicy(
            NamespaceSecurityPolicyDTO.builder()
                .mode(SecurityMode.COMMUNITY_SECURED)
                .activationState(SecurityActivationState.ACTIVE)
                .desiredPolicyVersion(99L)
                .requiredSigning(RequiredSigningDTO.builder().engineOutbound(true).build())
                .requiredAuthorization(
                    RequiredAuthorizationDTO.builder().startCommands(true).build())
                .activePolicyVersion(99L)
                .build());

    assertThat(validated.getDesiredPolicyHash()).isNotBlank();
    assertThat(validated.getActivePolicyHash()).isEqualTo(validated.getDesiredPolicyHash());
  }
}
