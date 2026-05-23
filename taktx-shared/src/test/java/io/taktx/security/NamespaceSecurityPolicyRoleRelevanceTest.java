/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.security;

import static org.assertj.core.api.Assertions.assertThat;

import io.taktx.dto.NamespaceSecurityPolicyDTO;
import io.taktx.dto.ParticipantRole;
import io.taktx.dto.RequiredAuthorizationDTO;
import io.taktx.dto.RequiredSigningDTO;
import io.taktx.dto.SecurityActivationState;
import io.taktx.dto.SecurityMode;
import org.junit.jupiter.api.Test;

class NamespaceSecurityPolicyRoleRelevanceTest {

  @Test
  void relevantElements_returnsExpectedEngineMatrix() {
    assertThat(NamespaceSecurityPolicyRoleRelevance.relevantElements(ParticipantRole.ENGINE))
        .contains(
            RoleRelevantPolicyElement.MODE,
            RoleRelevantPolicyElement.TRUST_ANCHOR_REQUIRED,
            RoleRelevantPolicyElement.REQUIRED_SIGNING_ENGINE_OUTBOUND,
            RoleRelevantPolicyElement.REQUIRED_AUTHORIZATION_START_COMMANDS,
            RoleRelevantPolicyElement.REQUIRED_AUTHORIZATION_EXTERNAL_TASK_COMPLETION,
            RoleRelevantPolicyElement.REQUIRED_AUTHORIZATION_USER_TASK_COMPLETION)
        .doesNotContain(RoleRelevantPolicyElement.REQUIRED_SIGNING_CLIENT_COMMANDS);
  }

  @Test
  void relevantPolicyForRole_filtersIrrelevantFieldsForWorker() {
    NamespaceSecurityPolicyDTO policy =
        NamespaceSecurityPolicyDTO.builder()
            .mode(SecurityMode.ANCHORED_SECURED)
            .activationState(SecurityActivationState.REQUESTED)
            .desiredPolicyVersion(42L)
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
            .build();

    NamespaceSecurityPolicyDTO relevant =
        NamespaceSecurityPolicyRoleRelevance.relevantPolicyForRole(ParticipantRole.WORKER, policy);

    assertThat(relevant.getMode()).isEqualTo(SecurityMode.ANCHORED_SECURED);
    assertThat(relevant.isTrustAnchorRequired()).isTrue();
    assertThat(relevant.getRequiredSigning().isWorkerResponses()).isTrue();
    assertThat(relevant.getRequiredSigning().isEngineOutbound()).isFalse();
    assertThat(relevant.getRequiredSigning().isClientCommands()).isFalse();
    assertThat(relevant.getRequiredAuthorization().isExternalTaskCompletion()).isTrue();
    assertThat(relevant.getRequiredAuthorization().isStartCommands()).isFalse();
    assertThat(relevant.getRequiredAuthorization().isUserTaskCompletion()).isFalse();
  }

  @Test
  void relevantPolicyForRole_filtersProtectedDataPlaneFieldsForConsole() {
    NamespaceSecurityPolicyDTO policy =
        NamespaceSecurityPolicyDTO.builder()
            .mode(SecurityMode.COMMUNITY_SECURED)
            .activationState(SecurityActivationState.REQUESTED)
            .desiredPolicyVersion(42L)
            .requiredSigning(RequiredSigningDTO.builder().engineOutbound(true).clientCommands(true).build())
            .requiredAuthorization(
                RequiredAuthorizationDTO.builder().startCommands(true).userTaskCompletion(true).build())
            .trustAnchorRequired(true)
            .build();

    NamespaceSecurityPolicyDTO relevant =
        NamespaceSecurityPolicyRoleRelevance.relevantPolicyForRole(ParticipantRole.CONSOLE, policy);

    assertThat(relevant.getMode()).isEqualTo(SecurityMode.COMMUNITY_SECURED);
    assertThat(relevant.isTrustAnchorRequired()).isTrue();
    assertThat(relevant.getRequiredSigning().isAnyRequired()).isFalse();
    assertThat(relevant.getRequiredAuthorization().isAnyRequired()).isFalse();
  }
}

