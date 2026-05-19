/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.serdes;

import io.taktx.dto.CancelCorrelationMessageSubscriptionDTO;
import io.taktx.dto.CancelDefinitionMessageSubscriptionDTO;
import io.taktx.dto.CorrelationMessageEventTriggerDTO;
import io.taktx.dto.CorrelationMessageSubscriptionDTO;
import io.taktx.dto.DefinitionMessageEventTriggerDTO;
import io.taktx.dto.DefinitionMessageSubscriptionDTO;
import io.taktx.dto.MessageEventDTO;
import io.taktx.dto.MessageEventKeyDTO;
import io.taktx.dto.ProcessDefinitionKey;
import io.taktx.dto.VariablesDTO;
import io.taktx.proto.CancelCorrelationMessageSubscriptionMessage;
import io.taktx.proto.CancelDefinitionMessageSubscriptionMessage;
import io.taktx.proto.CorrelationMessageEventTriggerMessage;
import io.taktx.proto.CorrelationMessageSubscriptionMessage;
import io.taktx.proto.DefinitionMessageEventTriggerMessage;
import io.taktx.proto.DefinitionMessageSubscriptionMessage;
import io.taktx.proto.MessageEventEnvelope;
import io.taktx.proto.MessageEventKeyMessage;
import io.taktx.proto.ProcessDefinitionKeyMessage;
import io.taktx.proto.Uuid;
import io.taktx.proto.VarMap;
import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;

/** Shared DTO ↔ protobuf mapper for message-event records. */
public final class MessageEventProtoMapper {

  private MessageEventProtoMapper() {}

  public static MessageEventEnvelope toProto(MessageEventDTO dto) {
    MessageEventEnvelope.Builder envelope = MessageEventEnvelope.newBuilder();
    if (dto == null) {
      return envelope.build();
    }
    if (dto instanceof DefinitionMessageSubscriptionDTO definitionSubscription) {
      envelope.setDefSub(toProto(definitionSubscription));
    } else if (dto instanceof CancelDefinitionMessageSubscriptionDTO cancelDefinition) {
      envelope.setCancelDefSub(toProto(cancelDefinition));
    } else if (dto instanceof CorrelationMessageSubscriptionDTO correlationSubscription) {
      envelope.setCorrSub(toProto(correlationSubscription));
    } else if (dto instanceof CancelCorrelationMessageSubscriptionDTO cancelCorrelation) {
      envelope.setCancelCorrSub(toProto(cancelCorrelation));
    } else if (dto instanceof DefinitionMessageEventTriggerDTO definitionTrigger) {
      envelope.setDefTrigger(toProto(definitionTrigger));
    } else if (dto instanceof CorrelationMessageEventTriggerDTO correlationTrigger) {
      envelope.setCorrTrigger(toProto(correlationTrigger));
    } else {
      throw new IllegalArgumentException(
          "Unsupported message-event type: " + dto.getClass().getName());
    }
    return envelope.build();
  }

  public static MessageEventDTO toDto(MessageEventEnvelope envelope) {
    if (envelope == null) {
      return null;
    }
    return switch (envelope.getEventCase()) {
      case DEF_SUB -> toDto(envelope.getDefSub());
      case CANCEL_DEF_SUB -> toDto(envelope.getCancelDefSub());
      case CORR_SUB -> toDto(envelope.getCorrSub());
      case CANCEL_CORR_SUB -> toDto(envelope.getCancelCorrSub());
      case DEF_TRIGGER -> toDto(envelope.getDefTrigger());
      case CORR_TRIGGER -> toDto(envelope.getCorrTrigger());
      case EVENT_NOT_SET -> null;
    };
  }

  public static MessageEventKeyMessage toProto(MessageEventKeyDTO dto) {
    MessageEventKeyMessage.Builder builder = MessageEventKeyMessage.newBuilder();
    if (dto != null && dto.getMessageName() != null) {
      builder.setMessageName(dto.getMessageName());
    }
    return builder.build();
  }

  public static MessageEventKeyDTO toDto(MessageEventKeyMessage message) {
    if (message == null) {
      return null;
    }
    return new MessageEventKeyDTO(emptyToNull(message.getMessageName()));
  }

  private static DefinitionMessageSubscriptionMessage toProto(
      DefinitionMessageSubscriptionDTO dto) {
    DefinitionMessageSubscriptionMessage.Builder builder =
        DefinitionMessageSubscriptionMessage.newBuilder();
    if (dto.getMessageName() != null) {
      builder.setMessageName(dto.getMessageName());
    }
    if (dto.getProcessDefinitionKey() != null) {
      builder.setProcessDefinitionKey(toProto(dto.getProcessDefinitionKey()));
    }
    if (dto.getElementId() != null) {
      builder.setElementId(dto.getElementId());
    }
    return builder.build();
  }

  private static DefinitionMessageSubscriptionDTO toDto(
      DefinitionMessageSubscriptionMessage message) {
    return new DefinitionMessageSubscriptionDTO(
        message.hasProcessDefinitionKey() ? toDto(message.getProcessDefinitionKey()) : null,
        emptyToNull(message.getElementId()),
        emptyToNull(message.getMessageName()));
  }

  private static CancelDefinitionMessageSubscriptionMessage toProto(
      CancelDefinitionMessageSubscriptionDTO dto) {
    CancelDefinitionMessageSubscriptionMessage.Builder builder =
        CancelDefinitionMessageSubscriptionMessage.newBuilder();
    if (dto.getMessageName() != null) {
      builder.setMessageName(dto.getMessageName());
    }
    return builder.build();
  }

  private static CancelDefinitionMessageSubscriptionDTO toDto(
      CancelDefinitionMessageSubscriptionMessage message) {
    return new CancelDefinitionMessageSubscriptionDTO(emptyToNull(message.getMessageName()));
  }

  private static CorrelationMessageSubscriptionMessage toProto(
      CorrelationMessageSubscriptionDTO dto) {
    CorrelationMessageSubscriptionMessage.Builder builder =
        CorrelationMessageSubscriptionMessage.newBuilder();
    if (dto.getMessageName() != null) {
      builder.setMessageName(dto.getMessageName());
    }
    if (dto.getProcessInstanceId() != null) {
      builder.setProcessInstanceId(toProto(dto.getProcessInstanceId()));
    }
    if (dto.getCorrelationKey() != null) {
      builder.setCorrelationKey(dto.getCorrelationKey());
    }
    if (dto.getElementInstanceIdPath() != null) {
      builder.addAllElementInstanceIdPath(dto.getElementInstanceIdPath());
    }
    if (dto.getElementId() != null) {
      builder.setElementId(dto.getElementId());
    }
    return builder.build();
  }

  private static CorrelationMessageSubscriptionDTO toDto(
      CorrelationMessageSubscriptionMessage message) {
    return new CorrelationMessageSubscriptionDTO(
        message.hasProcessInstanceId() ? toDto(message.getProcessInstanceId()) : null,
        emptyToNull(message.getCorrelationKey()),
        new ArrayList<>(message.getElementInstanceIdPathList()),
        emptyToNull(message.getElementId()),
        emptyToNull(message.getMessageName()));
  }

  private static CancelCorrelationMessageSubscriptionMessage toProto(
      CancelCorrelationMessageSubscriptionDTO dto) {
    CancelCorrelationMessageSubscriptionMessage.Builder builder =
        CancelCorrelationMessageSubscriptionMessage.newBuilder();
    if (dto.getMessageName() != null) {
      builder.setMessageName(dto.getMessageName());
    }
    if (dto.getCorrelationKey() != null) {
      builder.setCorrelationKey(dto.getCorrelationKey());
    }
    return builder.build();
  }

  private static CancelCorrelationMessageSubscriptionDTO toDto(
      CancelCorrelationMessageSubscriptionMessage message) {
    return new CancelCorrelationMessageSubscriptionDTO(
        emptyToNull(message.getMessageName()), emptyToNull(message.getCorrelationKey()));
  }

  private static DefinitionMessageEventTriggerMessage toProto(
      DefinitionMessageEventTriggerDTO dto) {
    DefinitionMessageEventTriggerMessage.Builder builder =
        DefinitionMessageEventTriggerMessage.newBuilder();
    if (dto.getMessageName() != null) {
      builder.setMessageName(dto.getMessageName());
    }
    if (dto.getVariables() != null) {
      builder.setVariables(toProto(dto.getVariables()));
    }
    return builder.build();
  }

  private static DefinitionMessageEventTriggerDTO toDto(
      DefinitionMessageEventTriggerMessage message) {
    return new DefinitionMessageEventTriggerDTO(
        emptyToNull(message.getMessageName()),
        message.hasVariables() ? toVariablesDto(message.getVariables()) : null);
  }

  private static CorrelationMessageEventTriggerMessage toProto(
      CorrelationMessageEventTriggerDTO dto) {
    CorrelationMessageEventTriggerMessage.Builder builder =
        CorrelationMessageEventTriggerMessage.newBuilder();
    if (dto.getMessageName() != null) {
      builder.setMessageName(dto.getMessageName());
    }
    if (dto.getCorrelationKey() != null) {
      builder.setCorrelationKey(dto.getCorrelationKey());
    }
    if (dto.getVariables() != null) {
      builder.setVariables(toProto(dto.getVariables()));
    }
    return builder.build();
  }

  private static CorrelationMessageEventTriggerDTO toDto(
      CorrelationMessageEventTriggerMessage message) {
    return new CorrelationMessageEventTriggerDTO(
        emptyToNull(message.getMessageName()),
        emptyToNull(message.getCorrelationKey()),
        message.hasVariables() ? toVariablesDto(message.getVariables()) : null);
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

  private static VarMap toProto(VariablesDTO variables) {
    return VarMap.newBuilder()
        .putAllEntries(variables == null ? Map.of() : variables.getVariables())
        .build();
  }

  private static VariablesDTO toVariablesDto(VarMap variables) {
    return VariablesDTO.ofVariableMap(variables.getEntriesMap());
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
