/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.serdes;

import io.taktx.dto.ParticipantCapability;
import io.taktx.dto.ParticipantEffectiveState;
import io.taktx.dto.ParticipantKind;
import io.taktx.dto.ParticipantStatusDTO;
import io.taktx.dto.PolicyMismatchReasonDTO;
import io.taktx.dto.SecurityMode;
import io.taktx.dto.StatusVerificationLevel;
import io.taktx.proto.ParticipantCapabilityMessage;
import io.taktx.proto.ParticipantEffectiveStateMessage;
import io.taktx.proto.ParticipantKindMessage;
import io.taktx.proto.ParticipantStatusMessage;
import io.taktx.proto.PolicyMismatchReasonMessage;
import io.taktx.proto.SecurityModeMessage;
import io.taktx.proto.StatusVerificationLevelMessage;
import java.util.LinkedHashSet;
import java.util.Map;

/** Shared DTO ↔ protobuf mapper for participant status records. */
public final class ParticipantStatusProtoMapper {

  private ParticipantStatusProtoMapper() {}

  public static ParticipantStatusMessage toProto(ParticipantStatusDTO dto) {
    ParticipantStatusMessage.Builder builder = ParticipantStatusMessage.newBuilder();
    if (dto == null) {
      return builder.build();
    }
    if (dto.getParticipantId() != null) {
      builder.setParticipantId(dto.getParticipantId());
    }
    if (dto.getParticipantInstanceId() != null) {
      builder.setParticipantInstanceId(dto.getParticipantInstanceId());
    }
    if (dto.getParticipantKind() != null) {
      builder.setParticipantKind(toProto(dto.getParticipantKind()));
    }
    if (dto.getComponentType() != null) {
      builder.setComponentType(dto.getComponentType());
    }
    if (dto.getCapabilities() != null) {
      builder.addAllCapabilities(
          dto.getCapabilities().stream().map(ParticipantStatusProtoMapper::toProto).toList());
    }
    if (dto.getSupportedModes() != null) {
      builder.addAllSupportedModes(
          dto.getSupportedModes().stream().map(ParticipantStatusProtoMapper::toProto).toList());
    }
    if (dto.getNamespace() != null) {
      builder.setNamespace(dto.getNamespace());
    }
    builder.setStartedAt(dto.getStartedAt());
    builder.setLastSeenAt(dto.getLastSeenAt());
    builder.setStatusExpiresAt(dto.getStatusExpiresAt());
    if (dto.getStatusVerificationLevel() != null) {
      builder.setStatusVerificationLevel(toProto(dto.getStatusVerificationLevel()));
    }
    if (dto.getEffectiveState() != null) {
      builder.setEffectiveState(toProto(dto.getEffectiveState()));
    }
    builder.setReadyForDataPlane(dto.isReadyForDataPlane());
    if (dto.getMismatchReasons() != null) {
      builder.addAllMismatchReasons(
          dto.getMismatchReasons().stream().map(ParticipantStatusProtoMapper::toProto).toList());
    }
    if (dto.getCurrentSigningKeyId() != null) {
      builder.setCurrentSigningKeyId(dto.getCurrentSigningKeyId());
    }
    return builder.build();
  }

  public static ParticipantStatusDTO toDto(ParticipantStatusMessage message) {
    if (message == null) {
      return null;
    }
    return ParticipantStatusDTO.builder()
        .participantId(emptyToNull(message.getParticipantId()))
        .participantInstanceId(emptyToNull(message.getParticipantInstanceId()))
        .participantKind(toDto(message.getParticipantKind()))
        .componentType(emptyToNull(message.getComponentType()))
        .capabilities(
            message.getCapabilitiesList().stream()
                .map(ParticipantStatusProtoMapper::toDto)
                .filter(java.util.Objects::nonNull)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new)))
        .supportedModes(
            message.getSupportedModesList().stream()
                .map(ParticipantStatusProtoMapper::toDto)
                .filter(java.util.Objects::nonNull)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new)))
        .namespace(emptyToNull(message.getNamespace()))
        .startedAt(message.getStartedAt())
        .lastSeenAt(message.getLastSeenAt())
        .statusExpiresAt(message.getStatusExpiresAt())
        .statusVerificationLevel(toDto(message.getStatusVerificationLevel()))
        .effectiveState(toDto(message.getEffectiveState()))
        .readyForDataPlane(message.getReadyForDataPlane())
        .mismatchReasons(
            message.getMismatchReasonsList().stream()
                .map(ParticipantStatusProtoMapper::toDto)
                .toList())
        .currentSigningKeyId(
            message.hasCurrentSigningKeyId() ? emptyToNull(message.getCurrentSigningKeyId()) : null)
        .build();
  }

  static PolicyMismatchReasonMessage toProto(PolicyMismatchReasonDTO dto) {
    PolicyMismatchReasonMessage.Builder builder = PolicyMismatchReasonMessage.newBuilder();
    if (dto == null) {
      return builder.build();
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

  static PolicyMismatchReasonDTO toDto(PolicyMismatchReasonMessage message) {
    return PolicyMismatchReasonDTO.builder()
        .code(emptyToNull(message.getCode()))
        .message(emptyToNull(message.getMessage()))
        .metadata(Map.copyOf(message.getMetadataMap()))
        .build();
  }

  private static ParticipantKindMessage toProto(ParticipantKind kind) {
    return switch (kind) {
      case ENGINE -> ParticipantKindMessage.ENGINE;
      case CLIENT -> ParticipantKindMessage.CLIENT;
    };
  }

  private static ParticipantKind toDto(ParticipantKindMessage kind) {
    return switch (kind) {
      case ENGINE -> ParticipantKind.ENGINE;
      case CLIENT -> ParticipantKind.CLIENT;
      case PARTICIPANT_KIND_UNSPECIFIED, UNRECOGNIZED -> null;
    };
  }

  private static ParticipantCapabilityMessage toProto(ParticipantCapability capability) {
    return switch (capability) {
      case ENFORCER -> ParticipantCapabilityMessage.ENFORCER;
      case PROTECTED_RUNTIME_PARTICIPANT ->
          ParticipantCapabilityMessage.PROTECTED_RUNTIME_PARTICIPANT;
      case SECURITY_OBSERVER -> ParticipantCapabilityMessage.SECURITY_OBSERVER;
    };
  }

  private static ParticipantCapability toDto(ParticipantCapabilityMessage capability) {
    return switch (capability) {
      case ENFORCER -> ParticipantCapability.ENFORCER;
      case PROTECTED_RUNTIME_PARTICIPANT -> ParticipantCapability.PROTECTED_RUNTIME_PARTICIPANT;
      case SECURITY_OBSERVER -> ParticipantCapability.SECURITY_OBSERVER;
      case PARTICIPANT_CAPABILITY_UNSPECIFIED, UNRECOGNIZED -> null;
    };
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

  private static StatusVerificationLevelMessage toProto(StatusVerificationLevel level) {
    return switch (level) {
      case UNVERIFIED_STATUS -> StatusVerificationLevelMessage.UNVERIFIED_STATUS;
      case LOCALLY_VERIFIED_STATUS -> StatusVerificationLevelMessage.LOCALLY_VERIFIED_STATUS;
    };
  }

  private static StatusVerificationLevel toDto(StatusVerificationLevelMessage level) {
    return switch (level) {
      case UNVERIFIED_STATUS -> StatusVerificationLevel.UNVERIFIED_STATUS;
      case LOCALLY_VERIFIED_STATUS -> StatusVerificationLevel.LOCALLY_VERIFIED_STATUS;
      case STATUS_VERIFICATION_LEVEL_UNSPECIFIED, UNRECOGNIZED -> null;
    };
  }

  private static ParticipantEffectiveStateMessage toProto(ParticipantEffectiveState state) {
    return switch (state) {
      case READY -> ParticipantEffectiveStateMessage.READY;
      case NOT_READY -> ParticipantEffectiveStateMessage.NOT_READY;
      case MISMATCH -> ParticipantEffectiveStateMessage.MISMATCH;
      case STALE -> ParticipantEffectiveStateMessage.STALE;
    };
  }

  private static ParticipantEffectiveState toDto(ParticipantEffectiveStateMessage state) {
    return switch (state) {
      case READY -> ParticipantEffectiveState.READY;
      case NOT_READY -> ParticipantEffectiveState.NOT_READY;
      case MISMATCH -> ParticipantEffectiveState.MISMATCH;
      case STALE -> ParticipantEffectiveState.STALE;
      case PARTICIPANT_EFFECTIVE_STATE_UNSPECIFIED, UNRECOGNIZED -> null;
    };
  }

  private static String emptyToNull(String value) {
    return value == null || value.isEmpty() ? null : value;
  }
}
