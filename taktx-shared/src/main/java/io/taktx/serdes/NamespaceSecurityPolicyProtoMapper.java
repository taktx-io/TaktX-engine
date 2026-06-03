/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.serdes;

import io.taktx.dto.NamespaceSecurityPolicyDTO;
import io.taktx.dto.SecurityMode;
import io.taktx.proto.NamespaceSecurityPolicyMessage;
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
        .policyVersion(message.hasPolicyVersion() ? message.getPolicyVersion() : null)
        .policyHash(emptyToNull(message.getPolicyHash()))
        .build();
  }

  private static SecurityModeMessage toProto(SecurityMode mode) {
    return switch (mode) {
      case OPEN -> SecurityModeMessage.OPEN;
      case ANCHORED -> SecurityModeMessage.ANCHORED;
    };
  }

  private static SecurityMode toDto(SecurityModeMessage mode) {
    return switch (mode) {
      case OPEN -> SecurityMode.OPEN;
      case ANCHORED -> SecurityMode.ANCHORED;
      case SECURITY_MODE_UNSPECIFIED, UNRECOGNIZED -> null;
    };
  }

  private static String emptyToNull(String value) {
    return value == null || value.isEmpty() ? null : value;
  }
}
