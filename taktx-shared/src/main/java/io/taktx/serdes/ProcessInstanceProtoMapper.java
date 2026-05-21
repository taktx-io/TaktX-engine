/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.serdes;

import io.taktx.dto.ExecutionState;
import io.taktx.dto.IncidentInfoDTO;
import io.taktx.dto.InstanceScheduleKeyDTO;
import io.taktx.dto.IoVariableMappingDTO;
import io.taktx.dto.ProcessDefinitionKey;
import io.taktx.dto.ProcessInstanceDTO;
import io.taktx.dto.ScopeDTO;
import io.taktx.dto.SubscriptionDTO;
import io.taktx.dto.SubscriptionsDTO;
import io.taktx.dto.TimeBucket;
import io.taktx.dto.subscriptions.CatchAllErrorSubscriptionDTO;
import io.taktx.dto.subscriptions.CatchAllEscalationSubscriptionDTO;
import io.taktx.dto.subscriptions.ErrorSubscriptionDTO;
import io.taktx.dto.subscriptions.EscalationSubscriptionDTO;
import io.taktx.dto.subscriptions.MessageSubscriptionDTO;
import io.taktx.dto.subscriptions.SignalSubscriptionDTO;
import io.taktx.dto.subscriptions.SubScriptionType;
import io.taktx.dto.subscriptions.TimerSubscriptionDTO;
import io.taktx.proto.CatchAllErrorSubscriptionMessage;
import io.taktx.proto.CatchAllEscalationSubscriptionMessage;
import io.taktx.proto.ErrorSubscriptionMessage;
import io.taktx.proto.EscalationSubscriptionMessage;
import io.taktx.proto.IncidentInfoMessage;
import io.taktx.proto.InstanceScheduleKeyMessage;
import io.taktx.proto.IoVariableMappingMessage;
import io.taktx.proto.MessageSubscriptionMessage;
import io.taktx.proto.ProcessDefinitionKeyMessage;
import io.taktx.proto.ProcessInstanceMessage;
import io.taktx.proto.ScheduleKeyEnvelope;
import io.taktx.proto.ScopeMessage;
import io.taktx.proto.SignalSubscriptionMessage;
import io.taktx.proto.SubscriptionEnvelope;
import io.taktx.proto.SubscriptionList;
import io.taktx.proto.SubscriptionsMessage;
import io.taktx.proto.TimerSubscriptionMessage;
import io.taktx.proto.Uuid;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Shared DTO ↔ protobuf mapper for process-instance state-store values. */
public final class ProcessInstanceProtoMapper {

  private ProcessInstanceProtoMapper() {}

  public static ProcessInstanceMessage toProto(ProcessInstanceDTO dto) {
    ProcessInstanceMessage.Builder builder = ProcessInstanceMessage.newBuilder();
    if (dto == null) {
      return builder.build();
    }
    if (dto.getProcessInstanceId() != null) {
      builder.setProcessInstanceId(toProto(dto.getProcessInstanceId()));
    }
    if (dto.getParentProcessInstanceId() != null) {
      builder.setParentProcessInstanceId(toProto(dto.getParentProcessInstanceId()));
    }
    if (dto.getScope() != null) {
      builder.setScope(toProto(dto.getScope()));
    }
    if (dto.getParentElementInstancePath() != null) {
      builder.addAllParentElementInstancePath(dto.getParentElementInstancePath());
    }
    if (dto.getProcessDefinitionKey() != null) {
      builder.setProcessDefinitionKey(toProto(dto.getProcessDefinitionKey()));
    }
    builder.setPropagateAllToParent(dto.isPropagateAllToParent());
    if (dto.getOutputMappings() != null) {
      dto.getOutputMappings().stream()
          .map(ProcessInstanceProtoMapper::toProto)
          .forEach(builder::addOutputMappings);
    }
    if (dto.getIncidentInfo() != null) {
      builder.setIncidentInfo(toProto(dto.getIncidentInfo()));
    }
    if (dto.getBusinessKey() != null) {
      builder.setBusinessKey(dto.getBusinessKey());
    }
    if (dto.getTags() != null) {
      builder.addAllTags(dto.getTags());
    }
    return builder.build();
  }

  public static ProcessInstanceDTO toDto(ProcessInstanceMessage message) {
    if (message == null) {
      return null;
    }
    return new ProcessInstanceDTO(
        message.hasProcessInstanceId() ? toDto(message.getProcessInstanceId()) : null,
        message.hasParentProcessInstanceId() ? toDto(message.getParentProcessInstanceId()) : null,
        message.hasScope() ? toDto(message.getScope()) : null,
        message.getParentElementInstancePathCount() == 0
            ? null
            : new ArrayList<>(message.getParentElementInstancePathList()),
        message.hasProcessDefinitionKey() ? toDto(message.getProcessDefinitionKey()) : null,
        message.getPropagateAllToParent(),
        toIoVariableMappingSet(message.getOutputMappingsList()),
        message.hasIncidentInfo() ? toDto(message.getIncidentInfo()) : null,
        emptyToNull(message.getBusinessKey()),
        message.getTagsCount() == 0 ? Set.of() : new LinkedHashSet<>(message.getTagsList()));
  }

  private static IncidentInfoMessage toProto(IncidentInfoDTO dto) {
    IncidentInfoMessage.Builder builder = IncidentInfoMessage.newBuilder();
    if (dto.getElementInstanceIdPath() != null) {
      builder.addAllElementInstanceIdPath(dto.getElementInstanceIdPath());
    }
    if (dto.getMessage() != null) {
      builder.setMessage(dto.getMessage());
    }
    if (dto.getStacktrace() != null) {
      builder.addAllStacktrace(List.of(dto.getStacktrace()));
    }
    if (dto.getDlqEntryRef() != null) {
      builder.setDlqEntryRef(dto.getDlqEntryRef());
    }
    return builder.build();
  }

  private static IncidentInfoDTO toDto(IncidentInfoMessage message) {
    return new IncidentInfoDTO(
        message.getElementInstanceIdPathCount() == 0
            ? null
            : new ArrayList<>(message.getElementInstanceIdPathList()),
        emptyToNull(message.getMessage()),
        message.getStacktraceCount() == 0
            ? null
            : message.getStacktraceList().toArray(String[]::new),
        emptyToNull(message.getDlqEntryRef()));
  }

  private static ScopeMessage toProto(ScopeDTO dto) {
    ScopeMessage.Builder builder = ScopeMessage.newBuilder();
    if (dto.getState() != null) {
      builder.setState(toProto(dto.getState()));
    }
    builder.setActiveCnt(dto.getActiveCnt());
    builder.setSubProcessLevel(dto.getSubProcessLevel());
    builder.setElementInstanceCnt(dto.getElementInstanceCnt());
    if (dto.getGatewayInstances() != null) {
      builder.putAllGatewayInstances(dto.getGatewayInstances());
    }
    if (dto.getSubscriptions() != null) {
      builder.setSubscriptions(toProto(dto.getSubscriptions()));
    }
    return builder.build();
  }

  private static ScopeDTO toDto(ScopeMessage message) {
    return new ScopeDTO(
        toDto(message.getState()),
        message.getActiveCnt(),
        message.getSubProcessLevel(),
        message.getElementInstanceCnt(),
        new LinkedHashMap<>(message.getGatewayInstancesMap()),
        message.hasSubscriptions() ? toDto(message.getSubscriptions()) : new SubscriptionsDTO());
  }

  private static SubscriptionsMessage toProto(SubscriptionsDTO dto) {
    SubscriptionsMessage.Builder builder = SubscriptionsMessage.newBuilder();
    if (dto.getInstanceSubscriptions() == null) {
      return builder.build();
    }
    dto.getInstanceSubscriptions()
        .forEach(
            (instanceId, subscriptions) -> {
              SubscriptionList.Builder listBuilder = SubscriptionList.newBuilder();
              if (subscriptions != null) {
                subscriptions.stream()
                    .map(ProcessInstanceProtoMapper::toProto)
                    .forEach(listBuilder::addItems);
              }
              builder.putInstanceSubscriptions(instanceId, listBuilder.build());
            });
    return builder.build();
  }

  private static SubscriptionsDTO toDto(SubscriptionsMessage message) {
    SubscriptionsDTO dto = new SubscriptionsDTO();
    if (message.getInstanceSubscriptionsCount() == 0) {
      return dto;
    }
    Map<Long, List<SubscriptionDTO>> subscriptions = new LinkedHashMap<>();
    message
        .getInstanceSubscriptionsMap()
        .forEach(
            (instanceId, list) -> {
              List<SubscriptionDTO> items = new ArrayList<>();
              list.getItemsList().stream()
                  .map(ProcessInstanceProtoMapper::toDto)
                  .forEach(items::add);
              subscriptions.put(instanceId, items);
            });
    dto.setInstanceSubscriptions(subscriptions);
    return dto;
  }

  private static SubscriptionEnvelope toProto(SubscriptionDTO dto) {
    SubscriptionEnvelope.Builder builder = SubscriptionEnvelope.newBuilder();
    if (dto instanceof CatchAllErrorSubscriptionDTO catchAllError) {
      CatchAllErrorSubscriptionMessage.Builder message =
          CatchAllErrorSubscriptionMessage.newBuilder();
      applyBaseSubscription(message, catchAllError);
      builder.setCatchAllError(message.build());
    } else if (dto instanceof ErrorSubscriptionDTO error) {
      ErrorSubscriptionMessage.Builder message = ErrorSubscriptionMessage.newBuilder();
      applyBaseSubscription(message, error);
      if (error.getCode() != null) {
        message.setCode(error.getCode());
      }
      builder.setErrorSub(message.build());
    } else if (dto instanceof CatchAllEscalationSubscriptionDTO catchAllEscalation) {
      CatchAllEscalationSubscriptionMessage.Builder message =
          CatchAllEscalationSubscriptionMessage.newBuilder();
      applyBaseSubscription(message, catchAllEscalation);
      builder.setCatchAllEsc(message.build());
    } else if (dto instanceof EscalationSubscriptionDTO escalation) {
      EscalationSubscriptionMessage.Builder message = EscalationSubscriptionMessage.newBuilder();
      applyBaseSubscription(message, escalation);
      if (escalation.getCode() != null) {
        message.setCode(escalation.getCode());
      }
      builder.setEscalationSub(message.build());
    } else if (dto instanceof MessageSubscriptionDTO messageSubscription) {
      MessageSubscriptionMessage.Builder message = MessageSubscriptionMessage.newBuilder();
      applyBaseSubscription(message, messageSubscription);
      if (messageSubscription.getName() != null) {
        message.setName(messageSubscription.getName());
      }
      if (messageSubscription.getCorrelationKey() != null) {
        message.setCorrelationKey(messageSubscription.getCorrelationKey());
      }
      builder.setMessageSub(message.build());
    } else if (dto instanceof TimerSubscriptionDTO timer) {
      TimerSubscriptionMessage.Builder message = TimerSubscriptionMessage.newBuilder();
      applyBaseSubscription(message, timer);
      if (timer.getScheduledKey() != null) {
        message.setScheduledKey(toProtoScheduleKey(timer.getScheduledKey()));
      }
      builder.setTimerSub(message.build());
    } else if (dto instanceof SignalSubscriptionDTO signal) {
      SignalSubscriptionMessage.Builder message = SignalSubscriptionMessage.newBuilder();
      applyBaseSubscription(message, signal);
      if (signal.getName() != null) {
        message.setName(signal.getName());
      }
      builder.setSignalSub(message.build());
    } else if (dto != null) {
      throw new IllegalArgumentException(
          "Unsupported subscription type: " + dto.getClass().getName());
    }
    return builder.build();
  }

  private static SubscriptionDTO toDto(SubscriptionEnvelope envelope) {
    return switch (envelope.getSubscriptionCase()) {
      case CATCH_ALL_ERROR -> {
        CatchAllErrorSubscriptionDTO dto = new CatchAllErrorSubscriptionDTO();
        applyBaseSubscription(
            dto,
            envelope.getCatchAllError().getSubScriptionType(),
            envelope.getCatchAllError().getElementId());
        yield dto;
      }
      case ERROR_SUB -> {
        ErrorSubscriptionDTO dto = new ErrorSubscriptionDTO();
        applyBaseSubscription(
            dto,
            envelope.getErrorSub().getSubScriptionType(),
            envelope.getErrorSub().getElementId());
        dto.setCode(emptyToNull(envelope.getErrorSub().getCode()));
        yield dto;
      }
      case CATCH_ALL_ESC -> {
        CatchAllEscalationSubscriptionDTO dto = new CatchAllEscalationSubscriptionDTO();
        applyBaseSubscription(
            dto,
            envelope.getCatchAllEsc().getSubScriptionType(),
            envelope.getCatchAllEsc().getElementId());
        yield dto;
      }
      case ESCALATION_SUB -> {
        EscalationSubscriptionDTO dto = new EscalationSubscriptionDTO();
        applyBaseSubscription(
            dto,
            envelope.getEscalationSub().getSubScriptionType(),
            envelope.getEscalationSub().getElementId());
        dto.setCode(emptyToNull(envelope.getEscalationSub().getCode()));
        yield dto;
      }
      case MESSAGE_SUB -> {
        MessageSubscriptionDTO dto = new MessageSubscriptionDTO();
        applyBaseSubscription(
            dto,
            envelope.getMessageSub().getSubScriptionType(),
            envelope.getMessageSub().getElementId());
        dto.setName(emptyToNull(envelope.getMessageSub().getName()));
        dto.setCorrelationKey(emptyToNull(envelope.getMessageSub().getCorrelationKey()));
        yield dto;
      }
      case TIMER_SUB -> {
        TimerSubscriptionDTO dto = new TimerSubscriptionDTO();
        applyBaseSubscription(
            dto,
            envelope.getTimerSub().getSubScriptionType(),
            envelope.getTimerSub().getElementId());
        dto.setScheduledKey(
            envelope.getTimerSub().hasScheduledKey()
                ? toInstanceScheduleKeyDto(envelope.getTimerSub().getScheduledKey())
                : null);
        yield dto;
      }
      case SIGNAL_SUB -> {
        SignalSubscriptionDTO dto = new SignalSubscriptionDTO();
        applyBaseSubscription(
            dto,
            envelope.getSignalSub().getSubScriptionType(),
            envelope.getSignalSub().getElementId());
        dto.setName(emptyToNull(envelope.getSignalSub().getName()));
        yield dto;
      }
      case SUBSCRIPTION_NOT_SET -> null;
    };
  }

  private static void applyBaseSubscription(
      CatchAllErrorSubscriptionMessage.Builder builder, SubscriptionDTO dto) {
    applyBaseSubscriptionCommon(builder, dto);
  }

  private static void applyBaseSubscription(
      ErrorSubscriptionMessage.Builder builder, SubscriptionDTO dto) {
    applyBaseSubscriptionCommon(builder, dto);
  }

  private static void applyBaseSubscription(
      CatchAllEscalationSubscriptionMessage.Builder builder, SubscriptionDTO dto) {
    applyBaseSubscriptionCommon(builder, dto);
  }

  private static void applyBaseSubscription(
      EscalationSubscriptionMessage.Builder builder, SubscriptionDTO dto) {
    applyBaseSubscriptionCommon(builder, dto);
  }

  private static void applyBaseSubscription(
      MessageSubscriptionMessage.Builder builder, SubscriptionDTO dto) {
    applyBaseSubscriptionCommon(builder, dto);
  }

  private static void applyBaseSubscription(
      TimerSubscriptionMessage.Builder builder, SubscriptionDTO dto) {
    applyBaseSubscriptionCommon(builder, dto);
  }

  private static void applyBaseSubscription(
      SignalSubscriptionMessage.Builder builder, SubscriptionDTO dto) {
    applyBaseSubscriptionCommon(builder, dto);
  }

  private static void applyBaseSubscriptionCommon(
      CatchAllErrorSubscriptionMessage.Builder builder, SubscriptionDTO dto) {
    if (dto.getSubScriptionType() != null) {
      builder.setSubScriptionType(toProto(dto.getSubScriptionType()));
    }
    if (dto.getElementId() != null) {
      builder.setElementId(dto.getElementId());
    }
  }

  private static void applyBaseSubscriptionCommon(
      ErrorSubscriptionMessage.Builder builder, SubscriptionDTO dto) {
    if (dto.getSubScriptionType() != null) {
      builder.setSubScriptionType(toProto(dto.getSubScriptionType()));
    }
    if (dto.getElementId() != null) {
      builder.setElementId(dto.getElementId());
    }
  }

  private static void applyBaseSubscriptionCommon(
      CatchAllEscalationSubscriptionMessage.Builder builder, SubscriptionDTO dto) {
    if (dto.getSubScriptionType() != null) {
      builder.setSubScriptionType(toProto(dto.getSubScriptionType()));
    }
    if (dto.getElementId() != null) {
      builder.setElementId(dto.getElementId());
    }
  }

  private static void applyBaseSubscriptionCommon(
      EscalationSubscriptionMessage.Builder builder, SubscriptionDTO dto) {
    if (dto.getSubScriptionType() != null) {
      builder.setSubScriptionType(toProto(dto.getSubScriptionType()));
    }
    if (dto.getElementId() != null) {
      builder.setElementId(dto.getElementId());
    }
  }

  private static void applyBaseSubscriptionCommon(
      MessageSubscriptionMessage.Builder builder, SubscriptionDTO dto) {
    if (dto.getSubScriptionType() != null) {
      builder.setSubScriptionType(toProto(dto.getSubScriptionType()));
    }
    if (dto.getElementId() != null) {
      builder.setElementId(dto.getElementId());
    }
  }

  private static void applyBaseSubscriptionCommon(
      TimerSubscriptionMessage.Builder builder, SubscriptionDTO dto) {
    if (dto.getSubScriptionType() != null) {
      builder.setSubScriptionType(toProto(dto.getSubScriptionType()));
    }
    if (dto.getElementId() != null) {
      builder.setElementId(dto.getElementId());
    }
  }

  private static void applyBaseSubscriptionCommon(
      SignalSubscriptionMessage.Builder builder, SubscriptionDTO dto) {
    if (dto.getSubScriptionType() != null) {
      builder.setSubScriptionType(toProto(dto.getSubScriptionType()));
    }
    if (dto.getElementId() != null) {
      builder.setElementId(dto.getElementId());
    }
  }

  private static void applyBaseSubscription(
      SubscriptionDTO dto, io.taktx.proto.SubScriptionType type, String elementId) {
    dto.setSubScriptionType(toDto(type));
    dto.setElementId(emptyToNull(elementId));
  }

  private static ScheduleKeyEnvelope toProtoScheduleKey(InstanceScheduleKeyDTO dto) {
    ScheduleKeyEnvelope.Builder builder = ScheduleKeyEnvelope.newBuilder();
    if (dto != null) {
      builder.setInstanceKey(toProto(dto));
    }
    return builder.build();
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

  private static InstanceScheduleKeyDTO toInstanceScheduleKeyDto(ScheduleKeyEnvelope envelope) {
    if (envelope == null || !envelope.hasInstanceKey()) {
      return null;
    }
    return toInstanceScheduleKeyDto(envelope.getInstanceKey());
  }

  private static InstanceScheduleKeyDTO toInstanceScheduleKeyDto(
      InstanceScheduleKeyMessage message) {
    return new InstanceScheduleKeyDTO(
        message.hasProcessInstanceId() ? toDto(message.getProcessInstanceId()) : null,
        new ArrayList<>(message.getElementInstanceIdPathList()),
        emptyToNull(message.getElementId()),
        toDto(message.getTimeBucket()));
  }

  private static io.taktx.proto.ExecutionState toProto(ExecutionState state) {
    return switch (state) {
      case INITIALIZED -> io.taktx.proto.ExecutionState.EXECUTION_STATE_INITIALIZED;
      case ACTIVE -> io.taktx.proto.ExecutionState.EXECUTION_STATE_ACTIVE;
      case COMPLETED -> io.taktx.proto.ExecutionState.EXECUTION_STATE_COMPLETED;
      case ABORTED -> io.taktx.proto.ExecutionState.EXECUTION_STATE_ABORTED;
    };
  }

  private static ExecutionState toDto(io.taktx.proto.ExecutionState state) {
    return switch (state) {
      case EXECUTION_STATE_ACTIVE -> ExecutionState.ACTIVE;
      case EXECUTION_STATE_COMPLETED -> ExecutionState.COMPLETED;
      case EXECUTION_STATE_ABORTED -> ExecutionState.ABORTED;
      case EXECUTION_STATE_UNSPECIFIED, EXECUTION_STATE_INITIALIZED, UNRECOGNIZED ->
          ExecutionState.INITIALIZED;
    };
  }

  private static io.taktx.proto.SubScriptionType toProto(SubScriptionType type) {
    return switch (type) {
      case STARTING -> io.taktx.proto.SubScriptionType.SUBSCRIPTION_TYPE_STARTING;
      case CONTINUING -> io.taktx.proto.SubScriptionType.SUBSCRIPTION_TYPE_UNSPECIFIED;
    };
  }

  private static SubScriptionType toDto(io.taktx.proto.SubScriptionType type) {
    return switch (type) {
      case SUBSCRIPTION_TYPE_STARTING -> SubScriptionType.STARTING;
      case SUBSCRIPTION_TYPE_UNSPECIFIED, UNRECOGNIZED -> SubScriptionType.CONTINUING;
    };
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

  private static IoVariableMappingMessage toProto(IoVariableMappingDTO dto) {
    IoVariableMappingMessage.Builder builder = IoVariableMappingMessage.newBuilder();
    if (dto.getSource() != null) {
      builder.setSource(dto.getSource());
    }
    if (dto.getTarget() != null) {
      builder.setTarget(dto.getTarget());
    }
    return builder.build();
  }

  private static Set<IoVariableMappingDTO> toIoVariableMappingSet(
      List<IoVariableMappingMessage> mappings) {
    if (mappings == null || mappings.isEmpty()) {
      return Set.of();
    }
    Set<IoVariableMappingDTO> result = new LinkedHashSet<>();
    mappings.stream().map(ProcessInstanceProtoMapper::toDto).forEach(result::add);
    return result;
  }

  private static IoVariableMappingDTO toDto(IoVariableMappingMessage message) {
    return new IoVariableMappingDTO(
        emptyToNull(message.getSource()), emptyToNull(message.getTarget()));
  }

  private static String emptyToNull(String value) {
    return value == null || value.isEmpty() ? null : value;
  }
}
