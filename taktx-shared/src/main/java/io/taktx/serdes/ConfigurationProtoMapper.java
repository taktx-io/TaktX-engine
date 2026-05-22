/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.serdes;

import io.taktx.dto.ConfigurationEventDTO;
import io.taktx.dto.DmnValidationMode;
import io.taktx.dto.GlobalConfigurationDTO;
import io.taktx.dto.ReplayProtectionMode;
import io.taktx.proto.ConfigurationEventMessage;
import io.taktx.proto.ConfigurationEventType;
import java.time.Instant;
import java.util.List;

/** Shared DTO ↔ protobuf mapper for runtime configuration records. */
public final class ConfigurationProtoMapper {

  private ConfigurationProtoMapper() {}

  public static ConfigurationEventMessage toProto(ConfigurationEventDTO dto) {
    ConfigurationEventMessage.Builder builder = ConfigurationEventMessage.newBuilder();
    if (dto == null) {
      return builder.build();
    }
    if (dto.getEventType() != null) {
      builder.setEventType(toProto(dto.getEventType()));
    }
    if (dto.getConfiguration() != null) {
      builder.setConfiguration(toProto(dto.getConfiguration()));
    }
    if (dto.getTimestamp() != null) {
      builder.setTimestampMs(dto.getTimestamp().toEpochMilli());
    }
    if (dto.getPublishedByInstance() != null) {
      builder.setPublishedByInstance(dto.getPublishedByInstance());
    }
    return builder.build();
  }

  public static ConfigurationEventDTO toDto(ConfigurationEventMessage message) {
    if (message == null) {
      return null;
    }
    return ConfigurationEventDTO.builder()
        .eventType(toDto(message.getEventType()))
        .configuration(message.hasConfiguration() ? toDto(message.getConfiguration()) : null)
        .timestamp(message.hasTimestampMs() ? Instant.ofEpochMilli(message.getTimestampMs()) : null)
        .publishedByInstance(emptyToNull(message.getPublishedByInstance()))
        .build();
  }

  public static io.taktx.proto.GlobalConfigurationMessage toProto(GlobalConfigurationDTO dto) {
    io.taktx.proto.GlobalConfigurationMessage.Builder builder =
        io.taktx.proto.GlobalConfigurationMessage.newBuilder();
    if (dto == null) {
      return builder.build();
    }
    builder.setSigningEnabled(dto.isSigningEnabled());
    builder.setEngineRequiresAuthorization(dto.isEngineRequiresAuthorization());
    builder.setEngineRequiresExternalTaskAuthorization(
        dto.isEngineRequiresExternalTaskAuthorization());
    builder.setEngineRequiresUserTaskAuthorization(dto.isEngineRequiresUserTaskAuthorization());
    List<String> trustedKeyIds = dto.getTrustedKeyIds();
    if (trustedKeyIds != null) {
      builder.addAllTrustedKeyIds(trustedKeyIds);
    }
    if (dto.getDmnValidationMode() != null) {
      builder.setDmnValidationMode(toProto(dto.getDmnValidationMode()));
    }
    if (dto.getReplayProtectionMode() != null) {
      builder.setReplayProtectionMode(toProto(dto.getReplayProtectionMode()));
    }
    builder.setReplayProtectionRetentionMs(dto.getReplayProtectionRetentionMs());
    return builder.build();
  }

  public static GlobalConfigurationDTO toDto(io.taktx.proto.GlobalConfigurationMessage message) {
    if (message == null) {
      return null;
    }
    return GlobalConfigurationDTO.builder()
        .signingEnabled(message.getSigningEnabled())
        .engineRequiresAuthorization(message.getEngineRequiresAuthorization())
        .engineRequiresExternalTaskAuthorization(
            message.getEngineRequiresExternalTaskAuthorization())
        .engineRequiresUserTaskAuthorization(message.getEngineRequiresUserTaskAuthorization())
        .trustedKeyIds(List.copyOf(message.getTrustedKeyIdsList()))
        .dmnValidationMode(toDto(message.getDmnValidationMode()))
        .replayProtectionMode(toDto(message.getReplayProtectionMode()))
        .replayProtectionRetentionMs(message.getReplayProtectionRetentionMs())
        .build();
  }

  private static io.taktx.proto.DmnValidationMode toProto(DmnValidationMode mode) {
    return switch (mode) {
      case WARN -> io.taktx.proto.DmnValidationMode.DMN_VALIDATION_WARN;
      case STRICT -> io.taktx.proto.DmnValidationMode.DMN_VALIDATION_STRICT;
      case PERMISSIVE -> io.taktx.proto.DmnValidationMode.DMN_VALIDATION_PERMISSIVE;
    };
  }

  private static DmnValidationMode toDto(io.taktx.proto.DmnValidationMode mode) {
    return switch (mode) {
      case DMN_VALIDATION_WARN -> DmnValidationMode.WARN;
      case DMN_VALIDATION_STRICT -> DmnValidationMode.STRICT;
      case DMN_VALIDATION_MODE_UNSPECIFIED, DMN_VALIDATION_PERMISSIVE, UNRECOGNIZED ->
          DmnValidationMode.PERMISSIVE;
    };
  }

  private static io.taktx.proto.ReplayProtectionMode toProto(ReplayProtectionMode mode) {
    return switch (mode) {
      case OFF -> io.taktx.proto.ReplayProtectionMode.OFF;
      case STRICT -> io.taktx.proto.ReplayProtectionMode.STRICT;
      case COMPAT -> io.taktx.proto.ReplayProtectionMode.COMPAT;
    };
  }

  private static ReplayProtectionMode toDto(io.taktx.proto.ReplayProtectionMode mode) {
    return switch (mode) {
      case OFF -> ReplayProtectionMode.OFF;
      case STRICT -> ReplayProtectionMode.STRICT;
      case REPLAY_PROTECTION_MODE_UNSPECIFIED, COMPAT, UNRECOGNIZED -> ReplayProtectionMode.COMPAT;
    };
  }

  private static ConfigurationEventType toProto(ConfigurationEventDTO.ConfigurationEventType type) {
    return switch (type) {
      case LICENSE_UPDATE -> ConfigurationEventType.LICENSE_UPDATE;
      case COMBINED_UPDATE -> ConfigurationEventType.COMBINED_UPDATE;
      case CONFIGURATION_UPDATE -> ConfigurationEventType.CONFIGURATION_UPDATE;
    };
  }

  private static ConfigurationEventDTO.ConfigurationEventType toDto(ConfigurationEventType type) {
    return switch (type) {
      case LICENSE_UPDATE -> ConfigurationEventDTO.ConfigurationEventType.LICENSE_UPDATE;
      case COMBINED_UPDATE -> ConfigurationEventDTO.ConfigurationEventType.COMBINED_UPDATE;
      case CONFIGURATION_EVENT_TYPE_UNSPECIFIED, CONFIGURATION_UPDATE, UNRECOGNIZED ->
          ConfigurationEventDTO.ConfigurationEventType.CONFIGURATION_UPDATE;
    };
  }

  private static String emptyToNull(String value) {
    return value == null || value.isEmpty() ? null : value;
  }
}
