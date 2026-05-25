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
import io.taktx.dto.ParticipantCapability;
import io.taktx.dto.RequiredAuthorizationDTO;
import io.taktx.dto.RequiredSigningDTO;
import io.taktx.dto.SecurityActivationState;
import io.taktx.dto.SecurityMode;
import java.util.Set;
import org.junit.jupiter.api.Test;

class NamespaceSecurityPolicyCapabilityRelevanceTest {

  @Test
  void relevantElements_returnsExpectedProtectedRuntimeMatrix() {
    assertThat(
            NamespaceSecurityPolicyCapabilityRelevance.relevantElements(
                Set.of(ParticipantCapability.PROTECTED_RUNTIME_PARTICIPANT)))
        .contains(
            CapabilityRelevantPolicyElement.MODE,
            CapabilityRelevantPolicyElement.TRUST_ANCHOR_REQUIRED,
            CapabilityRelevantPolicyElement.REQUIRED_SIGNING_CLIENT_COMMANDS,
            CapabilityRelevantPolicyElement.REQUIRED_AUTHORIZATION_START_COMMANDS,
            CapabilityRelevantPolicyElement.REQUIRED_SIGNING_WORKER_RESPONSES,
            CapabilityRelevantPolicyElement.REQUIRED_AUTHORIZATION_EXTERNAL_TASK_COMPLETION,
            CapabilityRelevantPolicyElement.REQUIRED_AUTHORIZATION_USER_TASK_COMPLETION);
  }

  @Test
  void relevantPolicyForCapabilities_filtersIrrelevantFieldsForWorkerLikeClient() {
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
        NamespaceSecurityPolicyCapabilityRelevance.relevantPolicyForCapabilities(
            Set.of(ParticipantCapability.PROTECTED_RUNTIME_PARTICIPANT), policy);

    assertThat(relevant.getMode()).isEqualTo(SecurityMode.ANCHORED_SECURED);
    assertThat(relevant.isTrustAnchorRequired()).isTrue();
    assertThat(relevant.getRequiredSigning().isWorkerResponses()).isTrue();
    assertThat(relevant.getRequiredSigning().isEngineOutbound()).isFalse();
    assertThat(relevant.getRequiredSigning().isClientCommands()).isTrue();
    assertThat(relevant.getRequiredAuthorization().isExternalTaskCompletion()).isTrue();
    assertThat(relevant.getRequiredAuthorization().isUserTaskCompletion()).isTrue();
    assertThat(relevant.getRequiredAuthorization().isStartCommands()).isTrue();
  }

  @Test
  void relevantPolicyForCapabilities_filtersProtectedDataPlaneFieldsForControlPlaneOnlyClient() {
    NamespaceSecurityPolicyDTO policy =
        NamespaceSecurityPolicyDTO.builder()
            .mode(SecurityMode.COMMUNITY_SECURED)
            .activationState(SecurityActivationState.REQUESTED)
            .desiredPolicyVersion(42L)
            .requiredSigning(
                RequiredSigningDTO.builder().engineOutbound(true).clientCommands(true).build())
            .requiredAuthorization(
                RequiredAuthorizationDTO.builder()
                    .startCommands(true)
                    .userTaskCompletion(true)
                    .build())
            .trustAnchorRequired(true)
            .build();

    NamespaceSecurityPolicyDTO relevant =
        NamespaceSecurityPolicyCapabilityRelevance.relevantPolicyForCapabilities(
            Set.of(
                ParticipantCapability.AUTHORITATIVE_POLICY_PUBLISHER,
                ParticipantCapability.SECURITY_OBSERVER),
            policy);

    assertThat(relevant.getMode()).isEqualTo(SecurityMode.COMMUNITY_SECURED);
    assertThat(relevant.isTrustAnchorRequired()).isTrue();
    assertThat(relevant.getRequiredSigning().isAnyRequired()).isFalse();
    assertThat(relevant.getRequiredAuthorization().isAnyRequired()).isFalse();
  }

  @Test
  void relevantPolicyForCapabilities_filtersProtectedDataPlaneFieldsForPublisherOnlyClient() {
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
        NamespaceSecurityPolicyCapabilityRelevance.relevantPolicyForCapabilities(
            Set.of(ParticipantCapability.AUTHORITATIVE_POLICY_PUBLISHER), policy);

    assertThat(relevant.getMode()).isEqualTo(SecurityMode.ANCHORED_SECURED);
    assertThat(relevant.isTrustAnchorRequired()).isTrue();
    assertThat(relevant.getRequiredSigning().isAnyRequired()).isFalse();
    assertThat(relevant.getRequiredAuthorization().isAnyRequired()).isFalse();
  }

  @Test
  void relevantPolicyForCapabilities_filtersProtectedDataPlaneFieldsForObserverOnlyClient() {
    NamespaceSecurityPolicyDTO policy =
        NamespaceSecurityPolicyDTO.builder()
            .mode(SecurityMode.COMMUNITY_SECURED)
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
        NamespaceSecurityPolicyCapabilityRelevance.relevantPolicyForCapabilities(
            Set.of(ParticipantCapability.SECURITY_OBSERVER), policy);

    assertThat(relevant.getMode()).isEqualTo(SecurityMode.COMMUNITY_SECURED);
    assertThat(relevant.isTrustAnchorRequired()).isTrue();
    assertThat(relevant.getRequiredSigning().isAnyRequired()).isFalse();
    assertThat(relevant.getRequiredAuthorization().isAnyRequired()).isFalse();
  }

  @Test
  void relevantPolicyForCapabilities_supportsMixedProfilesWithoutMultipleRoles() {
    NamespaceSecurityPolicyDTO policy =
        NamespaceSecurityPolicyDTO.builder()
            .mode(SecurityMode.ANCHORED_SECURED)
            .activationState(SecurityActivationState.REQUESTED)
            .desiredPolicyVersion(42L)
            .requiredSigning(
                RequiredSigningDTO.builder().clientCommands(true).workerResponses(true).build())
            .requiredAuthorization(
                RequiredAuthorizationDTO.builder()
                    .startCommands(true)
                    .externalTaskCompletion(true)
                    .userTaskCompletion(true)
                    .build())
            .trustAnchorRequired(true)
            .build();

    NamespaceSecurityPolicyDTO relevant =
        NamespaceSecurityPolicyCapabilityRelevance.relevantPolicyForCapabilities(
            Set.of(
                ParticipantCapability.PROTECTED_RUNTIME_PARTICIPANT,
                ParticipantCapability.SECURITY_OBSERVER),
            policy);

    assertThat(relevant.getRequiredSigning().isClientCommands()).isTrue();
    assertThat(relevant.getRequiredSigning().isWorkerResponses()).isTrue();
    assertThat(relevant.getRequiredAuthorization().isStartCommands()).isTrue();
    assertThat(relevant.getRequiredAuthorization().isExternalTaskCompletion()).isTrue();
    assertThat(relevant.getRequiredAuthorization().isUserTaskCompletion()).isTrue();
  }
}
