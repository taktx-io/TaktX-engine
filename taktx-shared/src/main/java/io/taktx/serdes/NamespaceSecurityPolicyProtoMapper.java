/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.serdes;

import io.taktx.dto.NamespaceSecurityPolicyDTO;
import io.taktx.dto.RequiredAuthorizationDTO;
import io.taktx.dto.RequiredSigningDTO;
import io.taktx.dto.SecurityActivationState;
import io.taktx.dto.SecurityMode;
import io.taktx.proto.NamespaceSecurityPolicyMessage;
import io.taktx.proto.RequiredAuthorizationMessage;
import io.taktx.proto.RequiredSigningMessage;
import io.taktx.proto.SecurityActivationStateMessage;
import io.taktx.proto.SecurityModeMessage;

/** Shared DTO ↔ protobuf mapper for namespace security policy records. */
public final class NamespaceSecurityPolicyProtoMapper {

  private NamespaceSecurityPolicyProtoMapper() {}

  public static NamespaceSecurityPolicyMessage toProto(NamespaceSecurityPolicyDTO dto) {
    NamespaceSecurityPolicyMessage.Builder builder = NamespaceSecurityPolicyMessage.newBuilder();
    if (dto == null) {
      return builder.build();
    }
    if (dto.getMode() != null) {
      builder.setMode(toProto(dto.getMode()));
    }
    if (dto.getActivationState() != null) {
      builder.setActivationState(toProto(dto.getActivationState()));
    }
    if (dto.getDesiredPolicyVersion() != null) {
      builder.setDesiredPolicyVersion(dto.getDesiredPolicyVersion());
    }
    if (dto.getDesiredPolicyHash() != null) {
      builder.setDesiredPolicyHash(dto.getDesiredPolicyHash());
    }
    if (dto.getActivePolicyVersion() != null) {
      builder.setActivePolicyVersion(dto.getActivePolicyVersion());
    }
    if (dto.getActivePolicyHash() != null) {
      builder.setActivePolicyHash(dto.getActivePolicyHash());
    }
    if (dto.getRequiredSigning() != null) {
      builder.setRequiredSigning(toProto(dto.getRequiredSigning()));
    }
    if (dto.getRequiredAuthorization() != null) {
      builder.setRequiredAuthorization(toProto(dto.getRequiredAuthorization()));
    }
    builder.setTrustAnchorRequired(dto.isTrustAnchorRequired());
    if (dto.getBreakGlassActor() != null) {
      builder.setBreakGlassActor(dto.getBreakGlassActor());
    }
    if (dto.getBreakGlassReason() != null) {
      builder.setBreakGlassReason(dto.getBreakGlassReason());
    }
    if (dto.getPolicyVersion() != null) {
      builder.setPolicyVersion(dto.getPolicyVersion());
    }
    if (dto.getPolicyHash() != null) {
      builder.setPolicyHash(dto.getPolicyHash());
    }
    return builder.build();
  }

  public static NamespaceSecurityPolicyDTO toDto(NamespaceSecurityPolicyMessage message) {
    if (message == null) {
      return null;
    }
    return NamespaceSecurityPolicyDTO.builder()
        .mode(toDto(message.getMode()))
        .activationState(toDto(message.getActivationState()))
        .desiredPolicyVersion(
            message.hasDesiredPolicyVersion() ? message.getDesiredPolicyVersion() : null)
        .desiredPolicyHash(emptyToNull(message.getDesiredPolicyHash()))
        .activePolicyVersion(
            message.hasActivePolicyVersion() ? message.getActivePolicyVersion() : null)
        .activePolicyHash(emptyToNull(message.getActivePolicyHash()))
        .requiredSigning(
            message.hasRequiredSigning()
                ? toDto(message.getRequiredSigning())
                : RequiredSigningDTO.builder().build())
        .requiredAuthorization(
            message.hasRequiredAuthorization()
                ? toDto(message.getRequiredAuthorization())
                : RequiredAuthorizationDTO.builder().build())
        .trustAnchorRequired(message.getTrustAnchorRequired())
        .breakGlassActor(emptyToNull(message.getBreakGlassActor()))
        .breakGlassReason(emptyToNull(message.getBreakGlassReason()))
        .policyVersion(message.hasPolicyVersion() ? message.getPolicyVersion() : null)
        .policyHash(emptyToNull(message.getPolicyHash()))
        .build();
  }

  private static RequiredSigningMessage toProto(RequiredSigningDTO dto) {
    return RequiredSigningMessage.newBuilder()
        .setEngineOutbound(dto.isEngineOutbound())
        .setClientCommands(dto.isClientCommands())
        .setWorkerResponses(dto.isWorkerResponses())
        .build();
  }

  private static RequiredSigningDTO toDto(RequiredSigningMessage message) {
    return RequiredSigningDTO.builder()
        .engineOutbound(message.getEngineOutbound())
        .clientCommands(message.getClientCommands())
        .workerResponses(message.getWorkerResponses())
        .build();
  }

  private static RequiredAuthorizationMessage toProto(RequiredAuthorizationDTO dto) {
    return RequiredAuthorizationMessage.newBuilder()
        .setStartCommands(dto.isStartCommands())
        .setExternalTaskCompletion(dto.isExternalTaskCompletion())
        .setUserTaskCompletion(dto.isUserTaskCompletion())
        .build();
  }

  private static RequiredAuthorizationDTO toDto(RequiredAuthorizationMessage message) {
    return RequiredAuthorizationDTO.builder()
        .startCommands(message.getStartCommands())
        .externalTaskCompletion(message.getExternalTaskCompletion())
        .userTaskCompletion(message.getUserTaskCompletion())
        .build();
  }

  private static SecurityModeMessage toProto(SecurityMode mode) {
    return switch (mode) {
      case COMMUNITY_OPEN -> SecurityModeMessage.COMMUNITY_OPEN;
      case COMMUNITY_SECURED -> SecurityModeMessage.COMMUNITY_SECURED;
      case ANCHORED_SECURED -> SecurityModeMessage.ANCHORED_SECURED;
      case MISCONFIGURED_SECURITY -> SecurityModeMessage.MISCONFIGURED_SECURITY;
    };
  }

  private static SecurityMode toDto(SecurityModeMessage mode) {
    return switch (mode) {
      case COMMUNITY_OPEN -> SecurityMode.COMMUNITY_OPEN;
      case COMMUNITY_SECURED -> SecurityMode.COMMUNITY_SECURED;
      case ANCHORED_SECURED -> SecurityMode.ANCHORED_SECURED;
      case MISCONFIGURED_SECURITY -> SecurityMode.MISCONFIGURED_SECURITY;
      case SECURITY_MODE_UNSPECIFIED, UNRECOGNIZED -> null;
    };
  }

  private static SecurityActivationStateMessage toProto(SecurityActivationState state) {
    return switch (state) {
      case REQUESTED -> SecurityActivationStateMessage.REQUESTED;
      case VALIDATING -> SecurityActivationStateMessage.VALIDATING;
      case ACTIVE -> SecurityActivationStateMessage.ACTIVE;
    };
  }

  private static SecurityActivationState toDto(SecurityActivationStateMessage state) {
    return switch (state) {
      case REQUESTED -> SecurityActivationState.REQUESTED;
      case VALIDATING -> SecurityActivationState.VALIDATING;
      case ACTIVE -> SecurityActivationState.ACTIVE;
      case SECURITY_ACTIVATION_STATE_UNSPECIFIED, UNRECOGNIZED -> null;
    };
  }

  private static String emptyToNull(String value) {
    return value == null || value.isEmpty() ? null : value;
  }
}
