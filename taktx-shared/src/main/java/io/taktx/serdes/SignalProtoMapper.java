/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.serdes;

import io.taktx.dto.CancelDefinitionSignalSubscriptionDTO;
import io.taktx.dto.CancelInstanceSignalSubscriptionDTO;
import io.taktx.dto.NewDefinitionSignalSubscriptionDTO;
import io.taktx.dto.NewInstanceSignalSubscriptionDTO;
import io.taktx.dto.ProcessDefinitionKey;
import io.taktx.dto.SignalDTO;
import io.taktx.proto.CancelDefinitionSignalSubscriptionMessage;
import io.taktx.proto.CancelInstanceSignalSubscriptionMessage;
import io.taktx.proto.NewDefinitionSignalSubscriptionMessage;
import io.taktx.proto.NewInstanceSignalSubscriptionMessage;
import io.taktx.proto.ProcessDefinitionKeyMessage;
import io.taktx.proto.SignalEnvelope;
import io.taktx.proto.SignalMessage;
import io.taktx.proto.Uuid;
import java.util.ArrayList;
import java.util.UUID;

/** Shared DTO ↔ protobuf mapper for signal records. */
public final class SignalProtoMapper {

  private SignalProtoMapper() {}

  public static SignalEnvelope toProto(SignalDTO dto) {
    SignalEnvelope.Builder envelope = SignalEnvelope.newBuilder();
    if (dto == null) {
      return envelope.build();
    }
    if (dto instanceof NewInstanceSignalSubscriptionDTO newInstanceSubscription) {
      envelope.setNewInstanceSub(toProto(newInstanceSubscription));
    } else if (dto instanceof CancelInstanceSignalSubscriptionDTO cancelInstanceSubscription) {
      envelope.setCancelInstanceSub(toProto(cancelInstanceSubscription));
    } else if (dto instanceof NewDefinitionSignalSubscriptionDTO newDefinitionSubscription) {
      envelope.setNewDefSub(toProto(newDefinitionSubscription));
    } else if (dto instanceof CancelDefinitionSignalSubscriptionDTO cancelDefinitionSubscription) {
      envelope.setCancelDefSub(toProto(cancelDefinitionSubscription));
    } else if (dto instanceof SignalDTO signal) {
      envelope.setSignalMsg(toProtoSignal(signal));
    } else {
      throw new IllegalArgumentException("Unsupported signal type: " + dto.getClass().getName());
    }
    return envelope.build();
  }

  public static SignalDTO toDto(SignalEnvelope envelope) {
    if (envelope == null) {
      return null;
    }
    return switch (envelope.getSignalCase()) {
      case SIGNAL_MSG -> toDtoSignal(envelope.getSignalMsg());
      case NEW_INSTANCE_SUB -> toDto(envelope.getNewInstanceSub());
      case CANCEL_INSTANCE_SUB -> toDto(envelope.getCancelInstanceSub());
      case NEW_DEF_SUB -> toDto(envelope.getNewDefSub());
      case CANCEL_DEF_SUB -> toDto(envelope.getCancelDefSub());
      case SIGNAL_NOT_SET -> null;
    };
  }

  private static SignalMessage toProtoSignal(SignalDTO dto) {
    SignalMessage.Builder builder = SignalMessage.newBuilder();
    if (dto.getSignalName() != null) {
      builder.setSignalName(dto.getSignalName());
    }
    return builder.build();
  }

  private static SignalDTO toDtoSignal(SignalMessage message) {
    return new SignalDTO(emptyToNull(message.getSignalName()));
  }

  private static NewInstanceSignalSubscriptionMessage toProto(
      NewInstanceSignalSubscriptionDTO dto) {
    NewInstanceSignalSubscriptionMessage.Builder builder =
        NewInstanceSignalSubscriptionMessage.newBuilder();
    if (dto.getSignalName() != null) {
      builder.setSignalName(dto.getSignalName());
    }
    if (dto.getProcessInstanceId() != null) {
      builder.setProcessInstanceId(toProto(dto.getProcessInstanceId()));
    }
    if (dto.getElementInstanceIdPath() != null) {
      builder.addAllElementInstanceIdPath(dto.getElementInstanceIdPath());
    }
    return builder.build();
  }

  private static NewInstanceSignalSubscriptionDTO toDto(
      NewInstanceSignalSubscriptionMessage message) {
    return new NewInstanceSignalSubscriptionDTO(
        message.hasProcessInstanceId() ? toDto(message.getProcessInstanceId()) : null,
        new ArrayList<>(message.getElementInstanceIdPathList()),
        emptyToNull(message.getSignalName()));
  }

  private static CancelInstanceSignalSubscriptionMessage toProto(
      CancelInstanceSignalSubscriptionDTO dto) {
    CancelInstanceSignalSubscriptionMessage.Builder builder =
        CancelInstanceSignalSubscriptionMessage.newBuilder();
    if (dto.getSignalName() != null) {
      builder.setSignalName(dto.getSignalName());
    }
    if (dto.getProcessInstanceId() != null) {
      builder.setProcessInstanceId(toProto(dto.getProcessInstanceId()));
    }
    if (dto.getElementInstanceIdPath() != null) {
      builder.addAllElementInstanceIdPath(dto.getElementInstanceIdPath());
    }
    return builder.build();
  }

  private static CancelInstanceSignalSubscriptionDTO toDto(
      CancelInstanceSignalSubscriptionMessage message) {
    return new CancelInstanceSignalSubscriptionDTO(
        message.hasProcessInstanceId() ? toDto(message.getProcessInstanceId()) : null,
        new ArrayList<>(message.getElementInstanceIdPathList()),
        emptyToNull(message.getSignalName()));
  }

  private static NewDefinitionSignalSubscriptionMessage toProto(
      NewDefinitionSignalSubscriptionDTO dto) {
    NewDefinitionSignalSubscriptionMessage.Builder builder =
        NewDefinitionSignalSubscriptionMessage.newBuilder();
    if (dto.getSignalName() != null) {
      builder.setSignalName(dto.getSignalName());
    }
    if (dto.getProcessDefinitionKey() != null) {
      builder.setProcessDefinitionKey(toProto(dto.getProcessDefinitionKey()));
    }
    if (dto.getElementId() != null) {
      builder.setElementId(dto.getElementId());
    }
    return builder.build();
  }

  private static NewDefinitionSignalSubscriptionDTO toDto(
      NewDefinitionSignalSubscriptionMessage message) {
    return new NewDefinitionSignalSubscriptionDTO(
        message.hasProcessDefinitionKey() ? toDto(message.getProcessDefinitionKey()) : null,
        emptyToNull(message.getElementId()),
        emptyToNull(message.getSignalName()));
  }

  private static CancelDefinitionSignalSubscriptionMessage toProto(
      CancelDefinitionSignalSubscriptionDTO dto) {
    CancelDefinitionSignalSubscriptionMessage.Builder builder =
        CancelDefinitionSignalSubscriptionMessage.newBuilder();
    if (dto.getSignalName() != null) {
      builder.setSignalName(dto.getSignalName());
    }
    if (dto.getProcessDefinitionKey() != null) {
      builder.setProcessDefinitionKey(toProto(dto.getProcessDefinitionKey()));
    }
    if (dto.getElementId() != null) {
      builder.setElementId(dto.getElementId());
    }
    return builder.build();
  }

  private static CancelDefinitionSignalSubscriptionDTO toDto(
      CancelDefinitionSignalSubscriptionMessage message) {
    return new CancelDefinitionSignalSubscriptionDTO(
        message.hasProcessDefinitionKey() ? toDto(message.getProcessDefinitionKey()) : null,
        emptyToNull(message.getElementId()),
        emptyToNull(message.getSignalName()));
  }

  private static ProcessDefinitionKeyMessage toProto(ProcessDefinitionKey key) {
    ProcessDefinitionKeyMessage.Builder builder = ProcessDefinitionKeyMessage.newBuilder();
    if (key != null) {
      if (key.getProcessDefinitionId() != null) {
        builder.setProcessDefinitionId(key.getProcessDefinitionId());
      }
      builder.setVersion(key.getVersion());
    }
    return builder.build();
  }

  private static ProcessDefinitionKey toDto(ProcessDefinitionKeyMessage message) {
    return new ProcessDefinitionKey(
        emptyToNull(message.getProcessDefinitionId()), message.getVersion());
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

  private static String emptyToNull(String value) {
    return value == null || value.isEmpty() ? null : value;
  }
}
