/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.serdes;

import io.taktx.dto.SecurityEventDTO;
import io.taktx.dto.SecurityEventSeverity;
import io.taktx.dto.SecurityEventType;
import io.taktx.proto.SecurityEventMessage;
import io.taktx.proto.SecurityEventSeverityMessage;
import io.taktx.proto.SecurityEventTypeMessage;
import java.util.Map;

/** Shared DTO ↔ protobuf mapper for append-only security event records. */
public final class SecurityEventProtoMapper {

  private SecurityEventProtoMapper() {}

  public static SecurityEventMessage toProto(SecurityEventDTO dto) {
    SecurityEventMessage.Builder builder = SecurityEventMessage.newBuilder();
    if (dto == null) {
      return builder.build();
    }
    if (dto.getEventType() != null) {
      builder.setEventType(toProto(dto.getEventType()));
    }
    if (dto.getSeverity() != null) {
      builder.setSeverity(toProto(dto.getSeverity()));
    }
    builder.setOccurredAtMs(dto.getOccurredAtMs());
    if (dto.getNamespace() != null) {
      builder.setNamespace(dto.getNamespace());
    }
    if (dto.getParticipantId() != null) {
      builder.setParticipantId(dto.getParticipantId());
    }
    if (dto.getParticipantInstanceId() != null) {
      builder.setParticipantInstanceId(dto.getParticipantInstanceId());
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
    if (dto.getCode() != null) {
      builder.setCode(dto.getCode());
    }
    if (dto.getMessage() != null) {
      builder.setMessage(dto.getMessage());
    }
    Map<String, String> metadata = dto.getMetadata();
    if (metadata != null) {
      builder.putAllMetadata(metadata);
    }
    return builder.build();
  }

  public static SecurityEventDTO toDto(SecurityEventMessage message) {
    if (message == null) {
      return null;
    }
    return SecurityEventDTO.builder()
        .eventType(toDto(message.getEventType()))
        .severity(toDto(message.getSeverity()))
        .occurredAtMs(message.getOccurredAtMs())
        .namespace(emptyToNull(message.getNamespace()))
        .participantId(emptyToNull(message.getParticipantId()))
        .participantInstanceId(emptyToNull(message.getParticipantInstanceId()))
        .desiredPolicyVersion(
            message.hasDesiredPolicyVersion() ? message.getDesiredPolicyVersion() : null)
        .desiredPolicyHash(emptyToNull(message.getDesiredPolicyHash()))
        .activePolicyVersion(
            message.hasActivePolicyVersion() ? message.getActivePolicyVersion() : null)
        .activePolicyHash(emptyToNull(message.getActivePolicyHash()))
        .code(emptyToNull(message.getCode()))
        .message(emptyToNull(message.getMessage()))
        .metadata(Map.copyOf(message.getMetadataMap()))
        .build();
  }

  private static SecurityEventTypeMessage toProto(SecurityEventType type) {
    return switch (type) {
      case POLICY_CHANGE -> SecurityEventTypeMessage.POLICY_CHANGE;
      case POLICY_REJECTION -> SecurityEventTypeMessage.POLICY_REJECTION;
      case READINESS_MISMATCH -> SecurityEventTypeMessage.READINESS_MISMATCH;
      case POLICY_DOWNGRADE -> SecurityEventTypeMessage.POLICY_DOWNGRADE;
      case TRUST_ANCHOR_PROBLEM -> SecurityEventTypeMessage.TRUST_ANCHOR_PROBLEM;
      case ACTIVATION_TIMEOUT -> SecurityEventTypeMessage.ACTIVATION_TIMEOUT;
      case ACTIVATION_ROLLBACK -> SecurityEventTypeMessage.ACTIVATION_ROLLBACK;
      case CONTROL_PLANE_MUTATION_REJECTED ->
          SecurityEventTypeMessage.CONTROL_PLANE_MUTATION_REJECTED;
      case DATA_PLANE_BLOCKED -> SecurityEventTypeMessage.DATA_PLANE_BLOCKED;
    };
  }

  private static SecurityEventType toDto(SecurityEventTypeMessage type) {
    return switch (type) {
      case POLICY_CHANGE -> SecurityEventType.POLICY_CHANGE;
      case POLICY_REJECTION -> SecurityEventType.POLICY_REJECTION;
      case READINESS_MISMATCH -> SecurityEventType.READINESS_MISMATCH;
      case POLICY_DOWNGRADE -> SecurityEventType.POLICY_DOWNGRADE;
      case TRUST_ANCHOR_PROBLEM -> SecurityEventType.TRUST_ANCHOR_PROBLEM;
      case ACTIVATION_TIMEOUT -> SecurityEventType.ACTIVATION_TIMEOUT;
      case ACTIVATION_ROLLBACK -> SecurityEventType.ACTIVATION_ROLLBACK;
      case CONTROL_PLANE_MUTATION_REJECTED -> SecurityEventType.CONTROL_PLANE_MUTATION_REJECTED;
      case DATA_PLANE_BLOCKED -> SecurityEventType.DATA_PLANE_BLOCKED;
      case SECURITY_EVENT_TYPE_UNSPECIFIED, UNRECOGNIZED -> null;
    };
  }

  private static SecurityEventSeverityMessage toProto(SecurityEventSeverity severity) {
    return switch (severity) {
      case INFO -> SecurityEventSeverityMessage.INFO;
      case WARNING -> SecurityEventSeverityMessage.WARNING;
      case ERROR -> SecurityEventSeverityMessage.ERROR;
      case CRITICAL -> SecurityEventSeverityMessage.CRITICAL;
    };
  }

  private static SecurityEventSeverity toDto(SecurityEventSeverityMessage severity) {
    return switch (severity) {
      case INFO -> SecurityEventSeverity.INFO;
      case WARNING -> SecurityEventSeverity.WARNING;
      case ERROR -> SecurityEventSeverity.ERROR;
      case CRITICAL -> SecurityEventSeverity.CRITICAL;
      case SECURITY_EVENT_SEVERITY_UNSPECIFIED, UNRECOGNIZED -> null;
    };
  }

  private static String emptyToNull(String value) {
    return value == null || value.isEmpty() ? null : value;
  }
}
