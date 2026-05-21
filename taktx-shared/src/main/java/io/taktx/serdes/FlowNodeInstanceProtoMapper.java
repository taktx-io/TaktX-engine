/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.serdes;

import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.MessageLite;
import com.google.protobuf.Parser;
import io.taktx.dto.ActivityInstanceDTO;
import io.taktx.dto.BoundaryEventInstanceDTO;
import io.taktx.dto.BusinessRuleTaskInstanceDTO;
import io.taktx.dto.CallActivityInstanceDTO;
import io.taktx.dto.DefinitionScheduleKeyDTO;
import io.taktx.dto.EndEventInstanceDTO;
import io.taktx.dto.EventBasedGatewayInstanceDTO;
import io.taktx.dto.ExclusiveGatewayInstanceDTO;
import io.taktx.dto.ExecutionState;
import io.taktx.dto.ExternalTaskInstanceDTO;
import io.taktx.dto.FlowNodeInstanceDTO;
import io.taktx.dto.GatewayInstanceDTO;
import io.taktx.dto.InclusiveGatewayInstanceDTO;
import io.taktx.dto.InstanceScheduleKeyDTO;
import io.taktx.dto.IntermediateCatchEventInstanceDTO;
import io.taktx.dto.IntermediateThrowEventInstanceDTO;
import io.taktx.dto.MessageEndEventInstanceDTO;
import io.taktx.dto.MessageEventKeyDTO;
import io.taktx.dto.MessageIntermediateThrowEventInstanceDTO;
import io.taktx.dto.MultiInstanceInstanceDTO;
import io.taktx.dto.ParallelGatewayInstanceDTO;
import io.taktx.dto.ProcessDefinitionKey;
import io.taktx.dto.ReceiveTaskInstanceDTO;
import io.taktx.dto.ScheduleKeyDTO;
import io.taktx.dto.ScopeDTO;
import io.taktx.dto.ScriptTaskInstanceDTO;
import io.taktx.dto.SendTaskInstanceDTO;
import io.taktx.dto.ServiceTaskInstanceDTO;
import io.taktx.dto.StartEventInstanceDTO;
import io.taktx.dto.SubProcessInstanceDTO;
import io.taktx.dto.SubscriptionDTO;
import io.taktx.dto.SubscriptionsDTO;
import io.taktx.dto.TaskInstanceDTO;
import io.taktx.dto.TimeBucket;
import io.taktx.dto.UserTaskInstanceDTO;
import io.taktx.dto.subscriptions.CatchAllErrorSubscriptionDTO;
import io.taktx.dto.subscriptions.CatchAllEscalationSubscriptionDTO;
import io.taktx.dto.subscriptions.ErrorSubscriptionDTO;
import io.taktx.dto.subscriptions.EscalationSubscriptionDTO;
import io.taktx.dto.subscriptions.MessageSubscriptionDTO;
import io.taktx.dto.subscriptions.SignalSubscriptionDTO;
import io.taktx.dto.subscriptions.SubScriptionType;
import io.taktx.dto.subscriptions.TimerSubscriptionDTO;
import io.taktx.proto.ActivityInstanceMessage;
import io.taktx.proto.BoundaryEventInstanceMessage;
import io.taktx.proto.BusinessRuleTaskInstanceMessage;
import io.taktx.proto.CallActivityInstanceMessage;
import io.taktx.proto.CatchAllErrorSubscriptionMessage;
import io.taktx.proto.CatchAllEscalationSubscriptionMessage;
import io.taktx.proto.CatchEventInstanceMessage;
import io.taktx.proto.CorrelationKeyListMessage;
import io.taktx.proto.DefinitionScheduleKeyMessage;
import io.taktx.proto.EndEventInstanceMessage;
import io.taktx.proto.ErrorSubscriptionMessage;
import io.taktx.proto.EscalationSubscriptionMessage;
import io.taktx.proto.EventBasedGatewayInstanceMessage;
import io.taktx.proto.ExclusiveGatewayInstanceMessage;
import io.taktx.proto.ExternalTaskInstanceMessage;
import io.taktx.proto.FlowNodeInstanceEnvelope;
import io.taktx.proto.FlowNodeInstanceMessage;
import io.taktx.proto.GatewayInstanceMessage;
import io.taktx.proto.InclusiveGatewayInstanceMessage;
import io.taktx.proto.InstanceScheduleKeyMessage;
import io.taktx.proto.IntermediateCatchEventInstanceMessage;
import io.taktx.proto.IntermediateThrowEventInstanceMessage;
import io.taktx.proto.MessageEndEventInstanceMessage;
import io.taktx.proto.MessageIntermediateThrowEventInstanceMessage;
import io.taktx.proto.MessageSubscriptionMessage;
import io.taktx.proto.MultiInstanceInstanceMessage;
import io.taktx.proto.ParallelGatewayInstanceMessage;
import io.taktx.proto.ProcessDefinitionKeyMessage;
import io.taktx.proto.ReceiveTaskInstanceMessage;
import io.taktx.proto.ScheduleKeyEnvelope;
import io.taktx.proto.ScopeMessage;
import io.taktx.proto.ScriptTaskInstanceMessage;
import io.taktx.proto.SendTaskInstanceMessage;
import io.taktx.proto.ServiceTaskInstanceMessage;
import io.taktx.proto.SignalSubscriptionMessage;
import io.taktx.proto.StartEventInstanceMessage;
import io.taktx.proto.SubProcessInstanceMessage;
import io.taktx.proto.SubscriptionEnvelope;
import io.taktx.proto.SubscriptionList;
import io.taktx.proto.SubscriptionsMessage;
import io.taktx.proto.TaskInstanceMessage;
import io.taktx.proto.ThrowEventInstanceMessage;
import io.taktx.proto.TimerSubscriptionMessage;
import io.taktx.proto.UserTaskInstanceMessage;
import io.taktx.proto.Uuid;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Shared DTO ↔ protobuf mapper for flow-node-instance records. */
public final class FlowNodeInstanceProtoMapper {

  private FlowNodeInstanceProtoMapper() {}

  public static FlowNodeInstanceEnvelope toProto(FlowNodeInstanceDTO dto) {
    FlowNodeInstanceEnvelope.Builder builder = FlowNodeInstanceEnvelope.newBuilder();
    if (dto == null) {
      return builder.build();
    }
    if (dto instanceof StartEventInstanceDTO startEvent) {
      builder.setStartEvent(toProto(startEvent));
    } else if (dto instanceof BoundaryEventInstanceDTO boundaryEvent) {
      builder.setBoundaryEvent(toProto(boundaryEvent));
    } else if (dto instanceof CallActivityInstanceDTO callActivity) {
      builder.setCallActivity(toProto(callActivity));
    } else if (dto instanceof SendTaskInstanceDTO sendTask) {
      builder.setSendTask(toProto(sendTask));
    } else if (dto instanceof EndEventInstanceDTO endEvent) {
      builder.setEndEvent(toProto(endEvent));
    } else if (dto instanceof ScriptTaskInstanceDTO scriptTask) {
      builder.setScriptTask(toProto(scriptTask));
    } else if (dto instanceof MessageEndEventInstanceDTO messageEndEvent) {
      builder.setMsgEndEvent(toProto(messageEndEvent));
    } else if (dto instanceof MessageIntermediateThrowEventInstanceDTO messageThrowEvent) {
      builder.setMsgThrowEvent(toProto(messageThrowEvent));
    } else if (dto instanceof IntermediateCatchEventInstanceDTO catchEvent) {
      builder.setCatchEvent(toProto(catchEvent));
    } else if (dto instanceof EventBasedGatewayInstanceDTO eventBasedGateway) {
      builder.setEbg(toProto(eventBasedGateway));
    } else if (dto instanceof BusinessRuleTaskInstanceDTO businessRuleTask) {
      builder.setBrt(toProto(businessRuleTask));
    } else if (dto instanceof MultiInstanceInstanceDTO multiInstance) {
      builder.setMultiInstance(toProto(multiInstance));
    } else if (dto instanceof InclusiveGatewayInstanceDTO inclusiveGateway) {
      builder.setInclusiveGw(toProto(inclusiveGateway));
    } else if (dto instanceof ParallelGatewayInstanceDTO parallelGateway) {
      builder.setParallelGw(toProto(parallelGateway));
    } else if (dto instanceof ReceiveTaskInstanceDTO receiveTask) {
      builder.setReceiveTask(toProto(receiveTask));
    } else if (dto instanceof SubProcessInstanceDTO subProcess) {
      builder.setSubProcess(toProto(subProcess));
    } else if (dto instanceof UserTaskInstanceDTO userTask) {
      builder.setUserTask(toProto(userTask));
    } else if (dto instanceof ServiceTaskInstanceDTO serviceTask) {
      builder.setServiceTask(toProto(serviceTask));
    } else if (dto instanceof TaskInstanceDTO task) {
      builder.setTask(toProto(task));
    } else if (dto instanceof IntermediateThrowEventInstanceDTO throwEvent) {
      builder.setThrowEvent(toProto(throwEvent));
    } else if (dto instanceof ExclusiveGatewayInstanceDTO exclusiveGateway) {
      builder.setExclusiveGw(toProto(exclusiveGateway));
    } else {
      throw new IllegalArgumentException(
          "Unsupported flow-node instance type: " + dto.getClass().getName());
    }
    return builder.build();
  }

  public static FlowNodeInstanceDTO toDto(FlowNodeInstanceEnvelope envelope) {
    if (envelope == null) {
      return null;
    }
    return switch (envelope.getInstanceCase()) {
      case START_EVENT -> toDto(envelope.getStartEvent());
      case BOUNDARY_EVENT -> toDto(envelope.getBoundaryEvent());
      case CALL_ACTIVITY -> toDto(envelope.getCallActivity());
      case SEND_TASK -> toDto(envelope.getSendTask());
      case END_EVENT -> toDto(envelope.getEndEvent());
      case SCRIPT_TASK -> toDto(envelope.getScriptTask());
      case MSG_END_EVENT -> toDto(envelope.getMsgEndEvent());
      case MSG_THROW_EVENT -> toDto(envelope.getMsgThrowEvent());
      case CATCH_EVENT -> toDto(envelope.getCatchEvent());
      case EBG -> toDto(envelope.getEbg());
      case BRT -> toDto(envelope.getBrt());
      case MULTI_INSTANCE -> toDto(envelope.getMultiInstance());
      case INCLUSIVE_GW -> toDto(envelope.getInclusiveGw());
      case PARALLEL_GW -> toDto(envelope.getParallelGw());
      case RECEIVE_TASK -> toDto(envelope.getReceiveTask());
      case SUB_PROCESS -> toDto(envelope.getSubProcess());
      case TASK -> toDto(envelope.getTask());
      case USER_TASK -> toDto(envelope.getUserTask());
      case SERVICE_TASK -> toDto(envelope.getServiceTask());
      case THROW_EVENT -> toDto(envelope.getThrowEvent());
      case EXCLUSIVE_GW -> toDto(envelope.getExclusiveGw());
      case INSTANCE_NOT_SET -> null;
    };
  }

  private static StartEventInstanceMessage toProto(StartEventInstanceDTO dto) {
    return mergeFrom(StartEventInstanceMessage.newBuilder(), toProtoCatchEvent(dto)).build();
  }

  private static BoundaryEventInstanceMessage toProto(BoundaryEventInstanceDTO dto) {
    BoundaryEventInstanceMessage.Builder builder =
        mergeFrom(BoundaryEventInstanceMessage.newBuilder(), toProtoCatchEvent(dto));
    builder.setAttachedInstanceId(dto.getAttachedInstanceId());
    return builder.build();
  }

  private static CallActivityInstanceMessage toProto(CallActivityInstanceDTO dto) {
    CallActivityInstanceMessage.Builder builder =
        mergeFrom(CallActivityInstanceMessage.newBuilder(), toProtoActivity(dto));
    if (dto.getChildProcessInstanceId() != null) {
      builder.setChildProcessInstanceId(toProto(dto.getChildProcessInstanceId()));
    }
    return builder.build();
  }

  private static SendTaskInstanceMessage toProto(SendTaskInstanceDTO dto) {
    return mergeFrom(SendTaskInstanceMessage.newBuilder(), toProtoExternalTask(dto)).build();
  }

  private static EndEventInstanceMessage toProto(EndEventInstanceDTO dto) {
    return mergeFrom(EndEventInstanceMessage.newBuilder(), toProtoThrowEvent(dto)).build();
  }

  private static ScriptTaskInstanceMessage toProto(ScriptTaskInstanceDTO dto) {
    return mergeFrom(ScriptTaskInstanceMessage.newBuilder(), toProtoExternalTask(dto)).build();
  }

  private static MessageEndEventInstanceMessage toProto(MessageEndEventInstanceDTO dto) {
    return mergeFrom(MessageEndEventInstanceMessage.newBuilder(), toProtoExternalTask(dto)).build();
  }

  private static MessageIntermediateThrowEventInstanceMessage toProto(
      MessageIntermediateThrowEventInstanceDTO dto) {
    return mergeFrom(
            MessageIntermediateThrowEventInstanceMessage.newBuilder(), toProtoExternalTask(dto))
        .build();
  }

  private static IntermediateCatchEventInstanceMessage toProto(
      IntermediateCatchEventInstanceDTO dto) {
    return mergeFrom(IntermediateCatchEventInstanceMessage.newBuilder(), toProtoCatchEvent(dto))
        .build();
  }

  private static EventBasedGatewayInstanceMessage toProto(EventBasedGatewayInstanceDTO dto) {
    EventBasedGatewayInstanceMessage.Builder builder =
        mergeFrom(EventBasedGatewayInstanceMessage.newBuilder(), toProtoGateway(dto));
    if (dto.getConnectedFlowNodeInstanceIds() != null) {
      builder.addAllConnectedFlowNodeInstanceIds(dto.getConnectedFlowNodeInstanceIds());
    }
    return builder.build();
  }

  private static BusinessRuleTaskInstanceMessage toProto(BusinessRuleTaskInstanceDTO dto) {
    return mergeFrom(BusinessRuleTaskInstanceMessage.newBuilder(), toProtoTask(dto)).build();
  }

  private static MultiInstanceInstanceMessage toProto(MultiInstanceInstanceDTO dto) {
    MultiInstanceInstanceMessage.Builder builder =
        mergeFrom(MultiInstanceInstanceMessage.newBuilder(), toProtoActivity(dto));
    if (dto.getScope() != null) {
      builder.setScope(toProto(dto.getScope()));
    }
    return builder.build();
  }

  private static InclusiveGatewayInstanceMessage toProto(InclusiveGatewayInstanceDTO dto) {
    InclusiveGatewayInstanceMessage.Builder builder =
        mergeFrom(InclusiveGatewayInstanceMessage.newBuilder(), toProtoGateway(dto));
    if (dto.getTriggeredInputFlows() != null) {
      builder.addAllTriggeredInputFlows(dto.getTriggeredInputFlows());
    }
    return builder.build();
  }

  private static ParallelGatewayInstanceMessage toProto(ParallelGatewayInstanceDTO dto) {
    ParallelGatewayInstanceMessage.Builder builder =
        mergeFrom(ParallelGatewayInstanceMessage.newBuilder(), toProtoGateway(dto));
    if (dto.getTriggeredFlows() != null) {
      builder.addAllTriggeredFlows(dto.getTriggeredFlows());
    }
    return builder.build();
  }

  private static ReceiveTaskInstanceMessage toProto(ReceiveTaskInstanceDTO dto) {
    ReceiveTaskInstanceMessage.Builder builder =
        mergeFrom(ReceiveTaskInstanceMessage.newBuilder(), toProtoTask(dto));
    if (dto.getCorrelationKey() != null) {
      builder.setCorrelationKey(dto.getCorrelationKey());
    }
    if (dto.getMessageEventKeys() != null) {
      dto.getMessageEventKeys()
          .forEach(
              (key, values) -> {
                if (key != null && key.getMessageName() != null) {
                  CorrelationKeyListMessage.Builder listBuilder =
                      CorrelationKeyListMessage.newBuilder();
                  if (values != null) {
                    listBuilder.addAllValues(values);
                  }
                  builder.putMessageEventKeys(key.getMessageName(), listBuilder.build());
                }
              });
    }
    return builder.build();
  }

  private static SubProcessInstanceMessage toProto(SubProcessInstanceDTO dto) {
    SubProcessInstanceMessage.Builder builder =
        mergeFrom(SubProcessInstanceMessage.newBuilder(), toProtoActivity(dto));
    if (dto.getScope() != null) {
      builder.setScope(toProto(dto.getScope()));
    }
    return builder.build();
  }

  private static TaskInstanceMessage toProto(TaskInstanceDTO dto) {
    return mergeFrom(TaskInstanceMessage.newBuilder(), toProtoActivity(dto)).build();
  }

  private static UserTaskInstanceMessage toProto(UserTaskInstanceDTO dto) {
    return mergeFrom(UserTaskInstanceMessage.newBuilder(), toProtoActivity(dto)).build();
  }

  private static ServiceTaskInstanceMessage toProto(ServiceTaskInstanceDTO dto) {
    return mergeFrom(ServiceTaskInstanceMessage.newBuilder(), toProtoExternalTask(dto)).build();
  }

  private static IntermediateThrowEventInstanceMessage toProto(
      IntermediateThrowEventInstanceDTO dto) {
    return mergeFrom(IntermediateThrowEventInstanceMessage.newBuilder(), toProtoThrowEvent(dto))
        .build();
  }

  private static ExclusiveGatewayInstanceMessage toProto(ExclusiveGatewayInstanceDTO dto) {
    return mergeFrom(ExclusiveGatewayInstanceMessage.newBuilder(), toProtoGateway(dto)).build();
  }

  private static StartEventInstanceDTO toDto(StartEventInstanceMessage message) {
    StartEventInstanceDTO dto = new StartEventInstanceDTO();
    applyBase(dto, parseBase(message, FlowNodeInstanceMessage.parser(), "FlowNodeInstanceMessage"));
    return dto;
  }

  private static BoundaryEventInstanceDTO toDto(BoundaryEventInstanceMessage message) {
    BoundaryEventInstanceDTO dto = new BoundaryEventInstanceDTO();
    applyBase(dto, parseBase(message, FlowNodeInstanceMessage.parser(), "FlowNodeInstanceMessage"));
    dto.setAttachedInstanceId(message.getAttachedInstanceId());
    return dto;
  }

  private static CallActivityInstanceDTO toDto(CallActivityInstanceMessage message) {
    CallActivityInstanceDTO dto = new CallActivityInstanceDTO();
    applyBase(
        (ActivityInstanceDTO) dto,
        parseBase(message, ActivityInstanceMessage.parser(), "ActivityInstanceMessage"));
    dto.setChildProcessInstanceId(
        message.hasChildProcessInstanceId() ? toDto(message.getChildProcessInstanceId()) : null);
    return dto;
  }

  private static SendTaskInstanceDTO toDto(SendTaskInstanceMessage message) {
    SendTaskInstanceDTO dto = new SendTaskInstanceDTO();
    applyBase(
        (ExternalTaskInstanceDTO) dto,
        parseBase(message, ExternalTaskInstanceMessage.parser(), "ExternalTaskInstanceMessage"));
    return dto;
  }

  private static EndEventInstanceDTO toDto(EndEventInstanceMessage message) {
    EndEventInstanceDTO dto = new EndEventInstanceDTO();
    applyBase(dto, parseBase(message, FlowNodeInstanceMessage.parser(), "FlowNodeInstanceMessage"));
    return dto;
  }

  private static ScriptTaskInstanceDTO toDto(ScriptTaskInstanceMessage message) {
    ScriptTaskInstanceDTO dto = new ScriptTaskInstanceDTO();
    applyBase(
        (ExternalTaskInstanceDTO) dto,
        parseBase(message, ExternalTaskInstanceMessage.parser(), "ExternalTaskInstanceMessage"));
    return dto;
  }

  private static MessageEndEventInstanceDTO toDto(MessageEndEventInstanceMessage message) {
    MessageEndEventInstanceDTO dto = new MessageEndEventInstanceDTO();
    applyBase(
        (ExternalTaskInstanceDTO) dto,
        parseBase(message, ExternalTaskInstanceMessage.parser(), "ExternalTaskInstanceMessage"));
    return dto;
  }

  private static MessageIntermediateThrowEventInstanceDTO toDto(
      MessageIntermediateThrowEventInstanceMessage message) {
    MessageIntermediateThrowEventInstanceDTO dto = new MessageIntermediateThrowEventInstanceDTO();
    applyBase(
        dto,
        parseBase(message, ExternalTaskInstanceMessage.parser(), "ExternalTaskInstanceMessage"));
    return dto;
  }

  private static IntermediateCatchEventInstanceDTO toDto(
      IntermediateCatchEventInstanceMessage message) {
    IntermediateCatchEventInstanceDTO dto = new IntermediateCatchEventInstanceDTO();
    applyBase(dto, parseBase(message, FlowNodeInstanceMessage.parser(), "FlowNodeInstanceMessage"));
    return dto;
  }

  private static EventBasedGatewayInstanceDTO toDto(EventBasedGatewayInstanceMessage message) {
    EventBasedGatewayInstanceDTO dto = new EventBasedGatewayInstanceDTO();
    applyBase(dto, parseBase(message, FlowNodeInstanceMessage.parser(), "FlowNodeInstanceMessage"));
    dto.setSelectedOutputFlows(
        message.getSelectedOutputFlowsCount() == 0
            ? null
            : new LinkedHashSet<>(message.getSelectedOutputFlowsList()));
    dto.setConnectedFlowNodeInstanceIds(
        message.getConnectedFlowNodeInstanceIdsCount() == 0
            ? null
            : new ArrayList<>(message.getConnectedFlowNodeInstanceIdsList()));
    return dto;
  }

  private static BusinessRuleTaskInstanceDTO toDto(BusinessRuleTaskInstanceMessage message) {
    BusinessRuleTaskInstanceDTO dto = new BusinessRuleTaskInstanceDTO();
    applyBase(
        (ActivityInstanceDTO) dto,
        parseBase(message, ActivityInstanceMessage.parser(), "ActivityInstanceMessage"));
    return dto;
  }

  private static MultiInstanceInstanceDTO toDto(MultiInstanceInstanceMessage message) {
    MultiInstanceInstanceDTO dto = new MultiInstanceInstanceDTO();
    applyBase(
        (ActivityInstanceDTO) dto,
        parseBase(message, ActivityInstanceMessage.parser(), "ActivityInstanceMessage"));
    dto.setScope(message.hasScope() ? toDto(message.getScope()) : null);
    return dto;
  }

  private static InclusiveGatewayInstanceDTO toDto(InclusiveGatewayInstanceMessage message) {
    InclusiveGatewayInstanceDTO dto = new InclusiveGatewayInstanceDTO();
    applyBase(
        (GatewayInstanceDTO) dto,
        parseBase(message, GatewayInstanceMessage.parser(), "GatewayInstanceMessage"));
    dto.setTriggeredInputFlows(
        message.getTriggeredInputFlowsCount() == 0
            ? null
            : new LinkedHashSet<>(message.getTriggeredInputFlowsList()));
    return dto;
  }

  private static ParallelGatewayInstanceDTO toDto(ParallelGatewayInstanceMessage message) {
    ParallelGatewayInstanceDTO dto = new ParallelGatewayInstanceDTO();
    applyBase(
        (GatewayInstanceDTO) dto,
        parseBase(message, GatewayInstanceMessage.parser(), "GatewayInstanceMessage"));
    dto.setTriggeredFlows(
        message.getTriggeredFlowsCount() == 0
            ? null
            : new LinkedHashSet<>(message.getTriggeredFlowsList()));
    return dto;
  }

  private static ReceiveTaskInstanceDTO toDto(ReceiveTaskInstanceMessage message) {
    ReceiveTaskInstanceDTO dto = new ReceiveTaskInstanceDTO();
    applyBase(
        (ActivityInstanceDTO) dto,
        parseBase(message, ActivityInstanceMessage.parser(), "ActivityInstanceMessage"));
    dto.setCorrelationKey(emptyToNull(message.getCorrelationKey()));
    if (message.getMessageEventKeysCount() > 0) {
      Map<MessageEventKeyDTO, Set<String>> messageEventKeys = new LinkedHashMap<>();
      message
          .getMessageEventKeysMap()
          .forEach(
              (messageName, values) ->
                  messageEventKeys.put(
                      new MessageEventKeyDTO(emptyToNull(messageName)),
                      values.getValuesCount() == 0
                          ? Set.of()
                          : new LinkedHashSet<>(values.getValuesList())));
      dto.setMessageEventKeys(messageEventKeys);
    }
    return dto;
  }

  private static SubProcessInstanceDTO toDto(SubProcessInstanceMessage message) {
    SubProcessInstanceDTO dto = new SubProcessInstanceDTO();
    applyBase(
        (ActivityInstanceDTO) dto,
        parseBase(message, ActivityInstanceMessage.parser(), "ActivityInstanceMessage"));
    dto.setScope(message.hasScope() ? toDto(message.getScope()) : null);
    return dto;
  }

  private static TaskInstanceDTO toDto(TaskInstanceMessage message) {
    TaskInstanceDTO dto = new TaskInstanceDTO();
    applyBase(
        (ActivityInstanceDTO) dto,
        parseBase(message, ActivityInstanceMessage.parser(), "ActivityInstanceMessage"));
    return dto;
  }

  private static UserTaskInstanceDTO toDto(UserTaskInstanceMessage message) {
    UserTaskInstanceDTO dto = new UserTaskInstanceDTO();
    applyBase(
        (ActivityInstanceDTO) dto,
        parseBase(message, ActivityInstanceMessage.parser(), "ActivityInstanceMessage"));
    return dto;
  }

  private static ServiceTaskInstanceDTO toDto(ServiceTaskInstanceMessage message) {
    ServiceTaskInstanceDTO dto = new ServiceTaskInstanceDTO();
    applyBase(
        (ExternalTaskInstanceDTO) dto,
        parseBase(message, ExternalTaskInstanceMessage.parser(), "ExternalTaskInstanceMessage"));
    return dto;
  }

  private static IntermediateThrowEventInstanceDTO toDto(
      IntermediateThrowEventInstanceMessage message) {
    IntermediateThrowEventInstanceDTO dto = new IntermediateThrowEventInstanceDTO();
    applyBase(dto, parseBase(message, FlowNodeInstanceMessage.parser(), "FlowNodeInstanceMessage"));
    return dto;
  }

  private static ExclusiveGatewayInstanceDTO toDto(ExclusiveGatewayInstanceMessage message) {
    ExclusiveGatewayInstanceDTO dto = new ExclusiveGatewayInstanceDTO();
    applyBase(
        (GatewayInstanceDTO) dto,
        parseBase(message, GatewayInstanceMessage.parser(), "GatewayInstanceMessage"));
    return dto;
  }

  private static FlowNodeInstanceMessage toProtoFlowNode(FlowNodeInstanceDTO dto) {
    FlowNodeInstanceMessage.Builder builder = FlowNodeInstanceMessage.newBuilder();
    if (dto.getState() != null) {
      builder.setState(toProto(dto.getState()));
    }
    builder.setElementInstanceId(dto.getElementInstanceId());
    builder.setParentElementInstanceId(dto.getParentElementInstanceId());
    builder.setElementIndex(dto.getElementIndex());
    if (dto.getElementId() != null) {
      builder.setElementId(dto.getElementId());
    }
    builder.setPassedCnt(dto.getPassedCnt());
    builder.setIncident(dto.isIncident());
    return builder.build();
  }

  private static CatchEventInstanceMessage toProtoCatchEvent(FlowNodeInstanceDTO dto) {
    return mergeFrom(CatchEventInstanceMessage.newBuilder(), toProtoFlowNode(dto)).build();
  }

  private static ThrowEventInstanceMessage toProtoThrowEvent(FlowNodeInstanceDTO dto) {
    return mergeFrom(ThrowEventInstanceMessage.newBuilder(), toProtoFlowNode(dto)).build();
  }

  private static GatewayInstanceMessage toProtoGateway(GatewayInstanceDTO dto) {
    GatewayInstanceMessage.Builder builder =
        mergeFrom(GatewayInstanceMessage.newBuilder(), toProtoFlowNode(dto));
    if (dto.getSelectedOutputFlows() != null) {
      builder.addAllSelectedOutputFlows(dto.getSelectedOutputFlows());
    }
    return builder.build();
  }

  private static ActivityInstanceMessage toProtoActivity(ActivityInstanceDTO dto) {
    ActivityInstanceMessage.Builder builder =
        mergeFrom(ActivityInstanceMessage.newBuilder(), toProtoFlowNode(dto));
    builder.setIteration(dto.isIteration());
    builder.setNextIterationId(dto.getNextIterationId());
    if (dto.getInputElement() != null) {
      builder.setInputElement(dto.getInputElement());
    }
    if (dto.getOutputElement() != null) {
      builder.setOutputElement(dto.getOutputElement());
    }
    builder.setLoopCnt(dto.getLoopCnt());
    return builder.build();
  }

  private static TaskInstanceMessage toProtoTask(TaskInstanceDTO dto) {
    return mergeFrom(TaskInstanceMessage.newBuilder(), toProtoActivity(dto)).build();
  }

  private static ExternalTaskInstanceMessage toProtoExternalTask(ExternalTaskInstanceDTO dto) {
    ExternalTaskInstanceMessage.Builder builder =
        mergeFrom(ExternalTaskInstanceMessage.newBuilder(), toProtoTask(dto));
    builder.setAttempt(dto.getAttempt());
    if (dto.getScheduledKeys() != null) {
      dto.getScheduledKeys().stream()
          .map(scheduleKey -> toProto((ScheduleKeyDTO) scheduleKey))
          .forEach(builder::addScheduledKeys);
    }
    return builder.build();
  }

  private static void applyBase(FlowNodeInstanceDTO dto, FlowNodeInstanceMessage message) {
    dto.setState(toDto(message.getState()));
    dto.setElementInstanceId(message.getElementInstanceId());
    dto.setParentElementInstanceId(message.getParentElementInstanceId());
    dto.setElementIndex(message.getElementIndex());
    dto.setElementId(emptyToNull(message.getElementId()));
    dto.setPassedCnt(message.getPassedCnt());
    dto.setIncident(message.getIncident());
  }

  private static void applyBase(ActivityInstanceDTO dto, ActivityInstanceMessage message) {
    applyBase(dto, parseBase(message, FlowNodeInstanceMessage.parser(), "FlowNodeInstanceMessage"));
    dto.setIteration(message.getIteration());
    dto.setNextIterationId(message.getNextIterationId());
    dto.setInputElement(message.hasInputElement() ? message.getInputElement() : null);
    dto.setOutputElement(message.hasOutputElement() ? message.getOutputElement() : null);
    dto.setLoopCnt(message.getLoopCnt());
  }

  private static void applyBase(GatewayInstanceDTO dto, GatewayInstanceMessage message) {
    applyBase(dto, parseBase(message, FlowNodeInstanceMessage.parser(), "FlowNodeInstanceMessage"));
    dto.setSelectedOutputFlows(
        message.getSelectedOutputFlowsCount() == 0
            ? null
            : new LinkedHashSet<>(message.getSelectedOutputFlowsList()));
  }

  private static void applyBase(ExternalTaskInstanceDTO dto, ExternalTaskInstanceMessage message) {
    applyBase(
        (ActivityInstanceDTO) dto,
        parseBase(message, ActivityInstanceMessage.parser(), "ActivityInstanceMessage"));
    dto.setAttempt(message.getAttempt());
    dto.setScheduledKeys(
        message.getScheduledKeysCount() == 0
            ? null
            : message.getScheduledKeysList().stream()
                .map(FlowNodeInstanceProtoMapper::toDto)
                .toList());
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
                    .map(FlowNodeInstanceProtoMapper::toProto)
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
                  .map(FlowNodeInstanceProtoMapper::toDto)
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
        message.setScheduledKey(toProto((ScheduleKeyDTO) timer.getScheduledKey()));
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
                ? (InstanceScheduleKeyDTO) toDto(envelope.getTimerSub().getScheduledKey())
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

  private static ScheduleKeyEnvelope toProto(ScheduleKeyDTO dto) {
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

  private static ScheduleKeyDTO toDto(ScheduleKeyEnvelope envelope) {
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

  private static <T extends MessageLite> T parseBase(
      MessageLite concreteMessage, Parser<T> parser, String targetType) {
    try {
      return parser.parseFrom(concreteMessage.toByteArray());
    } catch (InvalidProtocolBufferException e) {
      throw new IllegalStateException("Failed to parse " + targetType + " base projection", e);
    }
  }

  private static <B extends com.google.protobuf.MessageLite.Builder> B mergeFrom(
      B builder, MessageLite baseMessage) {
    try {
      builder.mergeFrom(baseMessage.toByteArray());
      return builder;
    } catch (InvalidProtocolBufferException e) {
      throw new IllegalStateException("Failed to merge compatible protobuf base fields", e);
    }
  }

  private static String emptyToNull(String value) {
    return value == null || value.isEmpty() ? null : value;
  }
}
