/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.serdes;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.protobuf.ByteString;
import io.taktx.dto.CommandAuthMethod;
import io.taktx.dto.CommandTrustMetadataDTO;
import io.taktx.dto.CommandTrustVerificationResult;
import io.taktx.dto.ExecutionState;
import io.taktx.dto.FlowNodeInstanceDTO;
import io.taktx.dto.FlowNodeInstanceUpdateDTO;
import io.taktx.dto.IncidentInfoDTO;
import io.taktx.dto.InstanceScheduleKeyDTO;
import io.taktx.dto.InstanceUpdateDTO;
import io.taktx.dto.ProcessDefinitionKey;
import io.taktx.dto.ProcessInstanceUpdateDTO;
import io.taktx.dto.ScopeDTO;
import io.taktx.dto.SubscriptionDTO;
import io.taktx.dto.SubscriptionsDTO;
import io.taktx.dto.TimeBucket;
import io.taktx.dto.VariablesDTO;
import io.taktx.jackson.TaktxObjectMappers;
import io.taktx.proto.CatchAllErrorSubscriptionMessage;
import io.taktx.proto.CatchAllEscalationSubscriptionMessage;
import io.taktx.proto.CommandTrustMetadataMessage;
import io.taktx.proto.ErrorSubscriptionMessage;
import io.taktx.proto.EscalationSubscriptionMessage;
import io.taktx.proto.IncidentInfoMessage;
import io.taktx.proto.InstanceScheduleKeyMessage;
import io.taktx.proto.InstanceUpdateEnvelope;
import io.taktx.proto.MessageSubscriptionMessage;
import io.taktx.proto.ProcessDefinitionKeyMessage;
import io.taktx.proto.ProcessInstanceUpdateMessage;
import io.taktx.proto.ScheduleKeyEnvelope;
import io.taktx.proto.ScopeMessage;
import io.taktx.proto.SignalSubscriptionMessage;
import io.taktx.proto.SubscriptionEnvelope;
import io.taktx.proto.SubscriptionList;
import io.taktx.proto.SubscriptionsMessage;
import io.taktx.proto.TimerSubscriptionMessage;
import io.taktx.proto.Uuid;
import io.taktx.proto.VarMap;
import io.taktx.variables.VariableValueDtoMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Shared DTO ↔ protobuf mapper for instance-update records. */
public final class InstanceUpdateProtoMapper {

  private static final ObjectMapper CBOR = TaktxObjectMappers.cbor();

  private InstanceUpdateProtoMapper() {}

  public static InstanceUpdateEnvelope toProto(InstanceUpdateDTO dto) {
    InstanceUpdateEnvelope.Builder builder = InstanceUpdateEnvelope.newBuilder();
    if (dto == null) {
      return builder.build();
    }
    if (dto instanceof FlowNodeInstanceUpdateDTO flowNodeUpdate) {
      builder.setFlowNode(toProto(flowNodeUpdate));
    } else if (dto instanceof ProcessInstanceUpdateDTO processUpdate) {
      builder.setProcess(toProto(processUpdate));
    } else {
      throw new IllegalArgumentException(
          "Unsupported instance update type: " + dto.getClass().getName());
    }
    return builder.build();
  }

  public static InstanceUpdateDTO toDto(InstanceUpdateEnvelope envelope) {
    if (envelope == null) {
      return null;
    }
    return switch (envelope.getUpdateCase()) {
      case FLOW_NODE -> toDto(envelope.getFlowNode());
      case PROCESS -> toDto(envelope.getProcess());
      case UPDATE_NOT_SET -> null;
    };
  }

  private static io.taktx.proto.FlowNodeInstanceUpdateMessage toProto(
      FlowNodeInstanceUpdateDTO dto) {
    io.taktx.proto.FlowNodeInstanceUpdateMessage.Builder builder =
        io.taktx.proto.FlowNodeInstanceUpdateMessage.newBuilder();
    applyTrustMetadata(builder, dto.getCurrentTrustMetadata(), dto.getOriginTrustMetadata());
    if (dto.getFlowNodeInstancePath() != null) {
      builder.addAllFlowNodeInstancePath(dto.getFlowNodeInstancePath());
    }
    if (dto.getFlowNodeInstance() != null) {
      builder.setFlowNodeInstance(ByteString.copyFrom(serializeFlowNodeInstance(dto.getFlowNodeInstance())));
    }
    if (dto.getVariables() != null) {
      builder.setVariables(toProto(dto.getVariables()));
    }
    builder.setProcessTime(dto.getProcessTime());
    if (dto.getInputSequenceFlowId() != null) {
      builder.setInputSequenceFlowId(dto.getInputSequenceFlowId());
    }
    if (dto.getOutputSequenceFlowIds() != null) {
      builder.addAllOutputSequenceFlowIds(dto.getOutputSequenceFlowIds());
    }
    return builder.build();
  }

  private static FlowNodeInstanceUpdateDTO toDto(
      io.taktx.proto.FlowNodeInstanceUpdateMessage message) {
    FlowNodeInstanceUpdateDTO dto =
        new FlowNodeInstanceUpdateDTO(
            message.getFlowNodeInstancePathCount() == 0
                ? null
                : new ArrayList<>(message.getFlowNodeInstancePathList()),
            message.hasFlowNodeInstance()
                ? deserializeFlowNodeInstance(message.getFlowNodeInstance().toByteArray())
                : null,
            message.hasVariables() ? toVariablesDto(message.getVariables()) : null,
            message.getProcessTime(),
            message.hasInputSequenceFlowId() ? message.getInputSequenceFlowId() : null,
            message.getOutputSequenceFlowIdsCount() == 0
                ? null
                : new ArrayList<>(message.getOutputSequenceFlowIdsList()));
    dto.setCurrentTrustMetadata(
        message.hasCurrentTrustMetadata() ? toDto(message.getCurrentTrustMetadata()) : null);
    dto.setOriginTrustMetadata(
        message.hasOriginTrustMetadata() ? toDto(message.getOriginTrustMetadata()) : null);
    return dto;
  }

  private static ProcessInstanceUpdateMessage toProto(ProcessInstanceUpdateDTO dto) {
    ProcessInstanceUpdateMessage.Builder builder = ProcessInstanceUpdateMessage.newBuilder();
    applyTrustMetadata(builder, dto.getCurrentTrustMetadata(), dto.getOriginTrustMetadata());
    if (dto.getParentProcessInstanceId() != null) {
      builder.setParentProcessInstanceId(toProto(dto.getParentProcessInstanceId()));
    }
    if (dto.getParentElementInstancePath() != null) {
      builder.addAllParentElementInstancePath(dto.getParentElementInstancePath());
    }
    if (dto.getProcessDefinitionKey() != null) {
      builder.setProcessDefinitionKey(toProto(dto.getProcessDefinitionKey()));
    }
    if (dto.getIncidentInfoDTO() != null) {
      builder.setIncidentInfo(toProto(dto.getIncidentInfoDTO()));
    }
    if (dto.getScope() != null) {
      builder.setScope(toProto(dto.getScope()));
    }
    if (dto.getVariables() != null) {
      builder.setVariables(toProto(dto.getVariables()));
    }
    if (dto.getProcessStartTime() != null) {
      builder.setProcessStartTime(dto.getProcessStartTime());
    }
    if (dto.getProcessEndTime() != null) {
      builder.setProcessEndTime(dto.getProcessEndTime());
    }
    if (dto.getBusinessKey() != null) {
      builder.setBusinessKey(dto.getBusinessKey());
    }
    if (dto.getTags() != null) {
      builder.addAllTags(dto.getTags());
    }
    return builder.build();
  }

  private static ProcessInstanceUpdateDTO toDto(ProcessInstanceUpdateMessage message) {
    ProcessInstanceUpdateDTO dto =
        new ProcessInstanceUpdateDTO(
            message.hasParentProcessInstanceId() ? toDto(message.getParentProcessInstanceId()) : null,
            message.getParentElementInstancePathCount() == 0
                ? null
                : new ArrayList<>(message.getParentElementInstancePathList()),
            message.hasProcessDefinitionKey() ? toDto(message.getProcessDefinitionKey()) : null,
            message.hasIncidentInfo() ? toDto(message.getIncidentInfo()) : null,
            message.hasScope() ? toDto(message.getScope()) : null,
            message.hasVariables() ? toVariablesDto(message.getVariables()) : null,
            message.hasProcessStartTime() ? message.getProcessStartTime() : null,
            message.hasProcessEndTime() ? message.getProcessEndTime() : null,
            message.hasBusinessKey() ? message.getBusinessKey() : null,
            message.getTagsCount() == 0 ? Set.of() : new LinkedHashSet<>(message.getTagsList()));
    dto.setCurrentTrustMetadata(
        message.hasCurrentTrustMetadata() ? toDto(message.getCurrentTrustMetadata()) : null);
    dto.setOriginTrustMetadata(
        message.hasOriginTrustMetadata() ? toDto(message.getOriginTrustMetadata()) : null);
    return dto;
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
        message.getStacktraceCount() == 0 ? null : message.getStacktraceList().toArray(String[]::new),
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
        message.getGatewayInstancesMap().isEmpty()
            ? null
            : new LinkedHashMap<>(message.getGatewayInstancesMap()),
        message.hasSubscriptions() ? toDto(message.getSubscriptions()) : null);
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
                subscriptions.stream().map(InstanceUpdateProtoMapper::toProto).forEach(listBuilder::addItems);
              }
              builder.putInstanceSubscriptions(instanceId, listBuilder.build());
            });
    return builder.build();
  }

  private static SubscriptionsDTO toDto(SubscriptionsMessage message) {
    SubscriptionsDTO dto = new SubscriptionsDTO();
    if (message.getInstanceSubscriptionsCount() == 0) {
      dto.setInstanceSubscriptions(null);
      return dto;
    }
    Map<Long, List<SubscriptionDTO>> subscriptions = new LinkedHashMap<>();
    message
        .getInstanceSubscriptionsMap()
        .forEach(
            (instanceId, list) -> {
              List<SubscriptionDTO> items = new ArrayList<>();
              list.getItemsList().stream().map(InstanceUpdateProtoMapper::toDto).forEach(items::add);
              subscriptions.put(instanceId, items);
            });
    dto.setInstanceSubscriptions(subscriptions);
    return dto;
  }

  private static SubscriptionEnvelope toProto(SubscriptionDTO dto) {
    SubscriptionEnvelope.Builder builder = SubscriptionEnvelope.newBuilder();
    if (dto instanceof io.taktx.dto.subscriptions.CatchAllErrorSubscriptionDTO catchAllError) {
      CatchAllErrorSubscriptionMessage.Builder message = CatchAllErrorSubscriptionMessage.newBuilder();
      applyBaseSubscription(message, catchAllError);
      builder.setCatchAllError(message.build());
    } else if (dto instanceof io.taktx.dto.subscriptions.ErrorSubscriptionDTO error) {
      ErrorSubscriptionMessage.Builder message = ErrorSubscriptionMessage.newBuilder();
      applyBaseSubscription(message, error);
      if (error.getCode() != null) {
        message.setCode(error.getCode());
      }
      builder.setErrorSub(message.build());
    } else if (dto instanceof io.taktx.dto.subscriptions.CatchAllEscalationSubscriptionDTO catchAllEscalation) {
      CatchAllEscalationSubscriptionMessage.Builder message =
          CatchAllEscalationSubscriptionMessage.newBuilder();
      applyBaseSubscription(message, catchAllEscalation);
      builder.setCatchAllEsc(message.build());
    } else if (dto instanceof io.taktx.dto.subscriptions.EscalationSubscriptionDTO escalation) {
      EscalationSubscriptionMessage.Builder message = EscalationSubscriptionMessage.newBuilder();
      applyBaseSubscription(message, escalation);
      if (escalation.getCode() != null) {
        message.setCode(escalation.getCode());
      }
      builder.setEscalationSub(message.build());
    } else if (dto instanceof io.taktx.dto.subscriptions.MessageSubscriptionDTO messageSubscription) {
      MessageSubscriptionMessage.Builder message = MessageSubscriptionMessage.newBuilder();
      applyBaseSubscription(message, messageSubscription);
      if (messageSubscription.getName() != null) {
        message.setName(messageSubscription.getName());
      }
      if (messageSubscription.getCorrelationKey() != null) {
        message.setCorrelationKey(messageSubscription.getCorrelationKey());
      }
      builder.setMessageSub(message.build());
    } else if (dto instanceof io.taktx.dto.subscriptions.TimerSubscriptionDTO timer) {
      TimerSubscriptionMessage.Builder message = TimerSubscriptionMessage.newBuilder();
      applyBaseSubscription(message, timer);
      if (timer.getScheduledKey() != null) {
        message.setScheduledKey(toProtoScheduleKey(timer.getScheduledKey()));
      }
      builder.setTimerSub(message.build());
    } else if (dto instanceof io.taktx.dto.subscriptions.SignalSubscriptionDTO signal) {
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
        io.taktx.dto.subscriptions.CatchAllErrorSubscriptionDTO dto =
            new io.taktx.dto.subscriptions.CatchAllErrorSubscriptionDTO();
        applyBaseSubscription(dto, envelope.getCatchAllError().getSubScriptionType(), envelope.getCatchAllError().getElementId());
        yield dto;
      }
      case ERROR_SUB -> {
        io.taktx.dto.subscriptions.ErrorSubscriptionDTO dto =
            new io.taktx.dto.subscriptions.ErrorSubscriptionDTO();
        applyBaseSubscription(dto, envelope.getErrorSub().getSubScriptionType(), envelope.getErrorSub().getElementId());
        dto.setCode(emptyToNull(envelope.getErrorSub().getCode()));
        yield dto;
      }
      case CATCH_ALL_ESC -> {
        io.taktx.dto.subscriptions.CatchAllEscalationSubscriptionDTO dto =
            new io.taktx.dto.subscriptions.CatchAllEscalationSubscriptionDTO();
        applyBaseSubscription(
            dto,
            envelope.getCatchAllEsc().getSubScriptionType(),
            envelope.getCatchAllEsc().getElementId());
        yield dto;
      }
      case ESCALATION_SUB -> {
        io.taktx.dto.subscriptions.EscalationSubscriptionDTO dto =
            new io.taktx.dto.subscriptions.EscalationSubscriptionDTO();
        applyBaseSubscription(
            dto,
            envelope.getEscalationSub().getSubScriptionType(),
            envelope.getEscalationSub().getElementId());
        dto.setCode(emptyToNull(envelope.getEscalationSub().getCode()));
        yield dto;
      }
      case MESSAGE_SUB -> {
        io.taktx.dto.subscriptions.MessageSubscriptionDTO dto =
            new io.taktx.dto.subscriptions.MessageSubscriptionDTO();
        applyBaseSubscription(
            dto,
            envelope.getMessageSub().getSubScriptionType(),
            envelope.getMessageSub().getElementId());
        dto.setName(emptyToNull(envelope.getMessageSub().getName()));
        dto.setCorrelationKey(emptyToNull(envelope.getMessageSub().getCorrelationKey()));
        yield dto;
      }
      case TIMER_SUB -> {
        io.taktx.dto.subscriptions.TimerSubscriptionDTO dto =
            new io.taktx.dto.subscriptions.TimerSubscriptionDTO();
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
        io.taktx.dto.subscriptions.SignalSubscriptionDTO dto =
            new io.taktx.dto.subscriptions.SignalSubscriptionDTO();
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

  private static InstanceScheduleKeyDTO toInstanceScheduleKeyDto(InstanceScheduleKeyMessage message) {
    return new InstanceScheduleKeyDTO(
        message.hasProcessInstanceId() ? toDto(message.getProcessInstanceId()) : null,
        message.getElementInstanceIdPathCount() == 0
            ? null
            : new ArrayList<>(message.getElementInstanceIdPathList()),
        emptyToNull(message.getElementId()),
        toDto(message.getTimeBucket()));
  }

  private static void applyTrustMetadata(
      io.taktx.proto.FlowNodeInstanceUpdateMessage.Builder builder,
      CommandTrustMetadataDTO currentTrustMetadata,
      CommandTrustMetadataDTO originTrustMetadata) {
    if (currentTrustMetadata != null) {
      builder.setCurrentTrustMetadata(toProto(currentTrustMetadata));
    }
    if (originTrustMetadata != null) {
      builder.setOriginTrustMetadata(toProto(originTrustMetadata));
    }
  }

  private static void applyTrustMetadata(
      ProcessInstanceUpdateMessage.Builder builder,
      CommandTrustMetadataDTO currentTrustMetadata,
      CommandTrustMetadataDTO originTrustMetadata) {
    if (currentTrustMetadata != null) {
      builder.setCurrentTrustMetadata(toProto(currentTrustMetadata));
    }
    if (originTrustMetadata != null) {
      builder.setOriginTrustMetadata(toProto(originTrustMetadata));
    }
  }

  private static CommandTrustMetadataMessage toProto(CommandTrustMetadataDTO dto) {
    CommandTrustMetadataMessage.Builder builder = CommandTrustMetadataMessage.newBuilder();
    if (dto.getAuthMethod() != null) {
      builder.setAuthMethod(toProto(dto.getAuthMethod()));
    }
    if (dto.getVerificationResult() != null) {
      builder.setVerificationResult(toProto(dto.getVerificationResult()));
    }
    if (dto.getTrusted() != null) {
      builder.setTrusted(dto.getTrusted());
    }
    if (dto.getUserId() != null) {
      builder.setUserId(dto.getUserId());
    }
    if (dto.getIssuer() != null) {
      builder.setIssuer(dto.getIssuer());
    }
    if (dto.getSignerKeyId() != null) {
      builder.setSignerKeyId(dto.getSignerKeyId());
    }
    if (dto.getSignerOwner() != null) {
      builder.setSignerOwner(dto.getSignerOwner());
    }
    if (dto.getSignerAlgorithm() != null) {
      builder.setSignerAlgorithm(dto.getSignerAlgorithm());
    }
    return builder.build();
  }

  private static CommandTrustMetadataDTO toDto(CommandTrustMetadataMessage message) {
    return CommandTrustMetadataDTO.builder()
        .authMethod(toDto(message.getAuthMethod()))
        .verificationResult(toDto(message.getVerificationResult()))
        .trusted(message.hasTrusted() ? message.getTrusted() : null)
        .userId(emptyToNull(message.getUserId()))
        .issuer(emptyToNull(message.getIssuer()))
        .signerKeyId(emptyToNull(message.getSignerKeyId()))
        .signerOwner(emptyToNull(message.getSignerOwner()))
        .signerAlgorithm(emptyToNull(message.getSignerAlgorithm()))
        .build();
  }

  private static io.taktx.proto.CommandAuthMethod toProto(CommandAuthMethod authMethod) {
    return switch (authMethod) {
      case JWT -> io.taktx.proto.CommandAuthMethod.COMMAND_AUTH_JWT;
      case ED25519 -> io.taktx.proto.CommandAuthMethod.COMMAND_AUTH_ED25519;
      case JWT_AND_ED25519 -> io.taktx.proto.CommandAuthMethod.COMMAND_AUTH_JWT_AND_ED25519;
      case NONE -> io.taktx.proto.CommandAuthMethod.COMMAND_AUTH_NONE;
    };
  }

  private static CommandAuthMethod toDto(io.taktx.proto.CommandAuthMethod authMethod) {
    return switch (authMethod) {
      case COMMAND_AUTH_JWT -> CommandAuthMethod.JWT;
      case COMMAND_AUTH_ED25519 -> CommandAuthMethod.ED25519;
      case COMMAND_AUTH_JWT_AND_ED25519 -> CommandAuthMethod.JWT_AND_ED25519;
      case COMMAND_AUTH_NONE -> CommandAuthMethod.NONE;
      case COMMAND_AUTH_METHOD_UNSPECIFIED, UNRECOGNIZED -> null;
    };
  }

  private static io.taktx.proto.CommandTrustVerificationResult toProto(
      CommandTrustVerificationResult verificationResult) {
    return switch (verificationResult) {
      case JWT_AUTHORIZED -> io.taktx.proto.CommandTrustVerificationResult.JWT_AUTHORIZED;
      case SIGNATURE_VERIFIED -> io.taktx.proto.CommandTrustVerificationResult.SIGNATURE_VERIFIED;
      case ENGINE_SIGNED -> io.taktx.proto.CommandTrustVerificationResult.ENGINE_SIGNED;
      case AUTHORIZATION_DISABLED ->
          io.taktx.proto.CommandTrustVerificationResult.AUTHORIZATION_DISABLED;
      case LICENSE_BYPASSED -> io.taktx.proto.CommandTrustVerificationResult.LICENSE_BYPASSED;
    };
  }

  private static CommandTrustVerificationResult toDto(
      io.taktx.proto.CommandTrustVerificationResult verificationResult) {
    return switch (verificationResult) {
      case JWT_AUTHORIZED -> CommandTrustVerificationResult.JWT_AUTHORIZED;
      case SIGNATURE_VERIFIED -> CommandTrustVerificationResult.SIGNATURE_VERIFIED;
      case ENGINE_SIGNED -> CommandTrustVerificationResult.ENGINE_SIGNED;
      case AUTHORIZATION_DISABLED -> CommandTrustVerificationResult.AUTHORIZATION_DISABLED;
      case LICENSE_BYPASSED -> CommandTrustVerificationResult.LICENSE_BYPASSED;
      case COMMAND_TRUST_VERIFICATION_UNSPECIFIED, UNRECOGNIZED -> null;
    };
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
      case EXECUTION_STATE_INITIALIZED -> ExecutionState.INITIALIZED;
      case EXECUTION_STATE_ACTIVE -> ExecutionState.ACTIVE;
      case EXECUTION_STATE_COMPLETED -> ExecutionState.COMPLETED;
      case EXECUTION_STATE_ABORTED -> ExecutionState.ABORTED;
      case EXECUTION_STATE_UNSPECIFIED, UNRECOGNIZED -> null;
    };
  }

  private static io.taktx.proto.SubScriptionType toProto(
      io.taktx.dto.subscriptions.SubScriptionType type) {
    return switch (type) {
      case STARTING -> io.taktx.proto.SubScriptionType.SUBSCRIPTION_TYPE_STARTING;
      case CONTINUING -> io.taktx.proto.SubScriptionType.SUBSCRIPTION_TYPE_UNSPECIFIED;
    };
  }

  private static io.taktx.dto.subscriptions.SubScriptionType toDto(
      io.taktx.proto.SubScriptionType type) {
    return switch (type) {
      case SUBSCRIPTION_TYPE_STARTING -> io.taktx.dto.subscriptions.SubScriptionType.STARTING;
      case SUBSCRIPTION_TYPE_UNSPECIFIED, UNRECOGNIZED ->
          io.taktx.dto.subscriptions.SubScriptionType.CONTINUING;
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

  private static VarMap toProto(VariablesDTO variables) {
    return VarMap.newBuilder().putAllEntries(VariableValueDtoMapper.toVariableMap(variables)).build();
  }

  private static VariablesDTO toVariablesDto(VarMap variables) {
    Map<String, io.taktx.proto.VariableValue> entries =
        variables == null ? Map.of() : variables.getEntriesMap();
    return VariableValueDtoMapper.toVariablesDto(entries);
  }

  private static byte[] serializeFlowNodeInstance(FlowNodeInstanceDTO dto) {
    try {
      return CBOR.writeValueAsBytes(dto);
    } catch (Exception e) {
      throw new IllegalStateException("Failed to serialize FlowNodeInstanceDTO bridge payload", e);
    }
  }

  private static FlowNodeInstanceDTO deserializeFlowNodeInstance(byte[] data) {
    try {
      return CBOR.readValue(data, FlowNodeInstanceDTO.class);
    } catch (Exception e) {
      throw new IllegalStateException("Failed to deserialize FlowNodeInstanceDTO bridge payload", e);
    }
  }

  private static String emptyToNull(String value) {
    return value == null || value.isEmpty() ? null : value;
  }
}


