/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.serdes;

import static org.assertj.core.api.Assertions.assertThat;

import io.taktx.dto.NamespaceSecurityPolicyDTO;
import io.taktx.dto.RequiredAuthorizationDTO;
import io.taktx.dto.RequiredSigningDTO;
import io.taktx.dto.SecurityActivationState;
import io.taktx.dto.SecurityMode;
import org.junit.jupiter.api.Test;

class NamespaceSecurityPolicyProtoMapperTest {

  @Test
  void namespaceSecurityPolicy_roundTripsThroughProto() {
    NamespaceSecurityPolicyDTO dto =
        NamespaceSecurityPolicyDTO.builder()
            .mode(SecurityMode.ANCHORED_SECURED)
            .activationState(SecurityActivationState.VALIDATING)
            .desiredPolicyVersion(42L)
            .desiredPolicyHash("abc123")
            .activePolicyVersion(41L)
            .activePolicyHash("def456")
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
            .breakGlassActor("ops-admin")
            .breakGlassReason("manual containment downgrade")
            .policyVersion(42L)
            .policyHash("abc123")
            .build();

    assertThat(
            NamespaceSecurityPolicyProtoMapper.toDto(
                NamespaceSecurityPolicyProtoMapper.toProto(dto)))
        .isEqualTo(dto);
  }

  @Test
  void namespaceSecurityPolicy_defaultsNestedRequirementsWhenProtoFieldsAreAbsent() {
    NamespaceSecurityPolicyDTO dto =
        NamespaceSecurityPolicyProtoMapper.toDto(
            io.taktx.proto.NamespaceSecurityPolicyMessage.newBuilder()
                .setMode(io.taktx.proto.SecurityModeMessage.OPEN)
                .setActivationState(io.taktx.proto.SecurityActivationStateMessage.REQUESTED)
                .setDesiredPolicyVersion(1L)
                .build());

    assertThat(dto.getRequiredSigning()).isEqualTo(RequiredSigningDTO.builder().build());
    assertThat(dto.getRequiredAuthorization())
        .isEqualTo(RequiredAuthorizationDTO.builder().build());
  }
}
