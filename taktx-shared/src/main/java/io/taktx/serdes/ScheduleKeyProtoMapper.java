/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.serdes;

import io.taktx.dto.DefinitionScheduleKeyDTO;
import io.taktx.dto.InstanceScheduleKeyDTO;
import io.taktx.dto.ProcessDefinitionKey;
import io.taktx.dto.ScheduleKeyDTO;
import io.taktx.dto.TimeBucket;
import io.taktx.proto.DefinitionScheduleKeyMessage;
import io.taktx.proto.InstanceScheduleKeyMessage;
import io.taktx.proto.ProcessDefinitionKeyMessage;
import io.taktx.proto.ScheduleKeyEnvelope;
import io.taktx.proto.Uuid;
import java.util.ArrayList;
import java.util.UUID;

/** Shared DTO ↔ protobuf mapper for schedule key records. */
public final class ScheduleKeyProtoMapper {

  private ScheduleKeyProtoMapper() {}

  public static ScheduleKeyEnvelope toProto(ScheduleKeyDTO dto) {
    ScheduleKeyEnvelope.Builder builder = ScheduleKeyEnvelope.newBuilder();
    if (dto instanceof DefinitionScheduleKeyDTO definitionKey) {
      builder.setDefinitionKey(toProto(definitionKey));
    } else if (dto instanceof InstanceScheduleKeyDTO instanceKey) {
      builder.setInstanceKey(toProto(instanceKey));
    } else if (dto != null) {
      throw new IllegalArgumentException(
          "Unsupported schedule key type: " + dto.getClass().getName());
    }
    return builder.build();
  }

  public static ScheduleKeyDTO toDto(ScheduleKeyEnvelope envelope) {
    if (envelope == null) {
      return null;
    }
    return switch (envelope.getKeyCase()) {
      case DEFINITION_KEY -> toDto(envelope.getDefinitionKey());
      case INSTANCE_KEY -> toDto(envelope.getInstanceKey());
      case KEY_NOT_SET -> null;
    };
  }

  private static DefinitionScheduleKeyMessage toProto(DefinitionScheduleKeyDTO dto) {
    DefinitionScheduleKeyMessage.Builder builder = DefinitionScheduleKeyMessage.newBuilder();
    if (dto.getTimeBucket() != null) {
      builder.setTimeBucket(toProto(dto.getTimeBucket()));
    }
    if (dto.getProcessDefinitionKey() != null) {
      builder.setProcessDefinitionKey(toProto(dto.getProcessDefinitionKey()));
    }
    if (dto.getFlowNodeId() != null) {
      builder.setFlowNodeId(dto.getFlowNodeId());
    }
    return builder.build();
  }

  private static DefinitionScheduleKeyDTO toDto(DefinitionScheduleKeyMessage message) {
    return new DefinitionScheduleKeyDTO(
        message.hasProcessDefinitionKey() ? toDto(message.getProcessDefinitionKey()) : null,
        emptyToNull(message.getFlowNodeId()),
        toDto(message.getTimeBucket()));
  }

  private static InstanceScheduleKeyMessage toProto(InstanceScheduleKeyDTO dto) {
    InstanceScheduleKeyMessage.Builder builder = InstanceScheduleKeyMessage.newBuilder();
    if (dto.getTimeBucket() != null) {
      builder.setTimeBucket(toProto(dto.getTimeBucket()));
    }
    if (dto.getProcessInstanceId() != null) {
      builder.setProcessInstanceId(toProto(dto.getProcessInstanceId()));
    }
    if (dto.getElementInstanceIdPath() != null) {
      builder.addAllElementInstanceIdPath(dto.getElementInstanceIdPath());
    }
    if (dto.getElementId() != null) {
      builder.setElementId(dto.getElementId());
    }
    return builder.build();
  }

  private static InstanceScheduleKeyDTO toDto(InstanceScheduleKeyMessage message) {
    return new InstanceScheduleKeyDTO(
        message.hasProcessInstanceId() ? toDto(message.getProcessInstanceId()) : null,
        message.getElementInstanceIdPathCount() == 0
            ? null
            : new ArrayList<>(message.getElementInstanceIdPathList()),
        emptyToNull(message.getElementId()),
        toDto(message.getTimeBucket()));
  }

  private static Uuid toProto(UUID uuid) {
    return Uuid.newBuilder()
        .setHigh(uuid.getMostSignificantBits())
        .setLow(uuid.getLeastSignificantBits())
        .build();
  }

  private static UUID toDto(Uuid uuid) {
    return new UUID(uuid.getHigh(), uuid.getLow());
  }

  private static ProcessDefinitionKeyMessage toProto(ProcessDefinitionKey key) {
    return ProcessDefinitionKeyMessage.newBuilder()
        .setProcessDefinitionId(key.getProcessDefinitionId())
        .setVersion(key.getVersion())
        .build();
  }

  private static ProcessDefinitionKey toDto(ProcessDefinitionKeyMessage key) {
    return new ProcessDefinitionKey(emptyToNull(key.getProcessDefinitionId()), key.getVersion());
  }

  private static io.taktx.proto.TimeBucket toProto(TimeBucket timeBucket) {
    return switch (timeBucket) {
      case MINUTE -> io.taktx.proto.TimeBucket.MINUTE;
      case HOURLY -> io.taktx.proto.TimeBucket.HOURLY;
      case DAILY -> io.taktx.proto.TimeBucket.DAILY;
      case WEEKLY -> io.taktx.proto.TimeBucket.WEEKLY;
      case YEARLY -> io.taktx.proto.TimeBucket.YEARLY;
    };
  }

  private static TimeBucket toDto(io.taktx.proto.TimeBucket timeBucket) {
    return switch (timeBucket) {
      case MINUTE -> TimeBucket.MINUTE;
      case HOURLY -> TimeBucket.HOURLY;
      case DAILY -> TimeBucket.DAILY;
      case WEEKLY -> TimeBucket.WEEKLY;
      case YEARLY, UNRECOGNIZED -> TimeBucket.YEARLY;
    };
  }

  private static String emptyToNull(String value) {
    return value == null || value.isEmpty() ? null : value;
  }
}
