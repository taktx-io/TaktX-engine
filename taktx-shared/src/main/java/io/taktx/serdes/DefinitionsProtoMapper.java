/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.serdes;

import io.taktx.dto.ActivityDTO;
import io.taktx.dto.AdHocSubProcessDTO;
import io.taktx.dto.AssignmentDefinitionDTO;
import io.taktx.dto.BaseElementDTO;
import io.taktx.dto.BoundaryEventDTO;
import io.taktx.dto.BusinessRuleTaskDTO;
import io.taktx.dto.CallActivityDTO;
import io.taktx.dto.CompensationEventDefinitionDTO;
import io.taktx.dto.DefinitionsKey;
import io.taktx.dto.DefinitionsTriggerDTO;
import io.taktx.dto.EndEventDTO;
import io.taktx.dto.ErrorDTO;
import io.taktx.dto.ErrorEventDefinitionDTO;
import io.taktx.dto.EscalationDTO;
import io.taktx.dto.EscalationEventDefinitionDTO;
import io.taktx.dto.EventBasedGatewayDTO;
import io.taktx.dto.EventDefinitionDTO;
import io.taktx.dto.ExclusiveGatewayDTO;
import io.taktx.dto.FlowConditionDTO;
import io.taktx.dto.FlowElementDTO;
import io.taktx.dto.FlowElementsDTO;
import io.taktx.dto.FlowNodeDTO;
import io.taktx.dto.GatewayDTO;
import io.taktx.dto.InclusiveGatewayDTO;
import io.taktx.dto.InputOutputMappingDTO;
import io.taktx.dto.IntermediateCatchEventDTO;
import io.taktx.dto.IntermediateThrowEventDTO;
import io.taktx.dto.IoVariableMappingDTO;
import io.taktx.dto.LinkEventDefinitionDTO;
import io.taktx.dto.LoopCharacteristicsDTO;
import io.taktx.dto.MessageDTO;
import io.taktx.dto.MessageEndEventDTO;
import io.taktx.dto.MessageEventDefinitionDTO;
import io.taktx.dto.MessageIntermediateThrowEventDTO;
import io.taktx.dto.ParallelGatewayDTO;
import io.taktx.dto.ParsedDefinitionsDTO;
import io.taktx.dto.PriorityDefinitionDTO;
import io.taktx.dto.ProcessDTO;
import io.taktx.dto.ProcessDefinitionActivationDTO;
import io.taktx.dto.ProcessDefinitionDTO;
import io.taktx.dto.ProcessDefinitionKey;
import io.taktx.dto.ProcessDefinitionStateEnum;
import io.taktx.dto.ReceiveTaskDTO;
import io.taktx.dto.ScriptTaskDTO;
import io.taktx.dto.ScriptType;
import io.taktx.dto.SendTaskDTO;
import io.taktx.dto.SequenceFlowDTO;
import io.taktx.dto.ServiceTaskDTO;
import io.taktx.dto.SigDTO;
import io.taktx.dto.SignalEventDefinitionDTO;
import io.taktx.dto.StartEventDTO;
import io.taktx.dto.SubProcessDTO;
import io.taktx.dto.TaskDTO;
import io.taktx.dto.TaskScheduleDTO;
import io.taktx.dto.TerminateEventDefinitionDTO;
import io.taktx.dto.TimerEventDefinitionDTO;
import io.taktx.dto.UserTaskDTO;
import io.taktx.dto.UserTaskTypeEnum;
import io.taktx.dto.XmlDefinitionsDTO;
import io.taktx.proto.AdHocSubProcessMessage;
import io.taktx.proto.AssignmentDefinitionMessage;
import io.taktx.proto.BaseElementEnvelope;
import io.taktx.proto.BoundaryEventMessage;
import io.taktx.proto.BusinessRuleTaskMessage;
import io.taktx.proto.CallActivityMessage;
import io.taktx.proto.CompensationEventDefinitionMessage;
import io.taktx.proto.DefinitionsKeyMessage;
import io.taktx.proto.DefinitionsTriggerEnvelope;
import io.taktx.proto.EndEventMessage;
import io.taktx.proto.ErrorDefinitionMessage;
import io.taktx.proto.ErrorEventDefinitionMessage;
import io.taktx.proto.EscalationDefinitionMessage;
import io.taktx.proto.EscalationEventDefinitionMessage;
import io.taktx.proto.EventBasedGatewayMessage;
import io.taktx.proto.ExclusiveGatewayMessage;
import io.taktx.proto.FlowConditionMessage;
import io.taktx.proto.FlowElementsMessage;
import io.taktx.proto.InclusiveGatewayMessage;
import io.taktx.proto.InputOutputMappingMessage;
import io.taktx.proto.IntermediateCatchEventMessage;
import io.taktx.proto.IntermediateThrowEventMessage;
import io.taktx.proto.IoVariableMappingMessage;
import io.taktx.proto.LinkEventDefinitionMessage;
import io.taktx.proto.LoopCharacteristicsMessage;
import io.taktx.proto.MessageEndEventMessage;
import io.taktx.proto.MessageEventDefinitionMessage;
import io.taktx.proto.MessageIntermediateThrowEventMessage;
import io.taktx.proto.MessageMessage;
import io.taktx.proto.ParallelGatewayMessage;
import io.taktx.proto.ParsedDefinitionsMessage;
import io.taktx.proto.PriorityDefinitionMessage;
import io.taktx.proto.ProcessDefinitionActivationMessage;
import io.taktx.proto.ProcessDefinitionKeyMessage;
import io.taktx.proto.ProcessDefinitionMessage;
import io.taktx.proto.ProcessMessage;
import io.taktx.proto.ReceiveTaskMessage;
import io.taktx.proto.ScriptTaskMessage;
import io.taktx.proto.SendTaskMessage;
import io.taktx.proto.SequenceFlowMessage;
import io.taktx.proto.ServiceTaskMessage;
import io.taktx.proto.SigMessage;
import io.taktx.proto.SignalEventDefinitionMessage;
import io.taktx.proto.StartEventMessage;
import io.taktx.proto.SubProcessMessage;
import io.taktx.proto.TaskMessage;
import io.taktx.proto.TaskScheduleMessage;
import io.taktx.proto.TerminateEventDefinitionMessage;
import io.taktx.proto.TimerEventDefinitionMessage;
import io.taktx.proto.UserTaskMessage;
import io.taktx.proto.XmlDefinitionsMessage;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Shared DTO ↔ protobuf mapper for process-definition and BPMN element records. */
public final class DefinitionsProtoMapper {

  private DefinitionsProtoMapper() {}

  public static DefinitionsTriggerEnvelope toProto(DefinitionsTriggerDTO dto) {
    DefinitionsTriggerEnvelope.Builder builder = DefinitionsTriggerEnvelope.newBuilder();
    if (dto == null) {
      return builder.build();
    }
    if (dto instanceof XmlDefinitionsDTO xmlDefinitions) {
      builder.setXmlDefs(toProto(xmlDefinitions));
    } else if (dto instanceof ParsedDefinitionsDTO parsedDefinitions) {
      builder.setParsedDefs(toProto(parsedDefinitions));
    } else if (dto instanceof ProcessDefinitionActivationDTO activation) {
      builder.setActivation(toProto(activation));
    } else {
      throw new IllegalArgumentException(
          "Unsupported definitions trigger type: " + dto.getClass().getName());
    }
    return builder.build();
  }

  public static DefinitionsTriggerDTO toDto(DefinitionsTriggerEnvelope envelope) {
    if (envelope == null) {
      return null;
    }
    return switch (envelope.getTriggerCase()) {
      case XML_DEFS -> toDto(envelope.getXmlDefs());
      case PARSED_DEFS -> toDto(envelope.getParsedDefs());
      case ACTIVATION -> toDto(envelope.getActivation());
      case TRIGGER_NOT_SET -> null;
    };
  }

  public static ParsedDefinitionsMessage toProto(ParsedDefinitionsDTO dto) {
    ParsedDefinitionsMessage.Builder builder = ParsedDefinitionsMessage.newBuilder();
    if (dto == null) {
      return builder.build();
    }
    if (dto.getDefinitionsKey() != null) {
      builder.setDefinitionsKey(toProto(dto.getDefinitionsKey()));
    }
    if (dto.getRootProcess() != null) {
      builder.setRootProcess(toProto(dto.getRootProcess()));
    }
    putMap(dto.getMessages(), builder::putMessages, DefinitionsProtoMapper::toProto);
    putMap(dto.getEscalations(), builder::putEscalations, DefinitionsProtoMapper::toProto);
    putMap(dto.getErrors(), builder::putErrors, DefinitionsProtoMapper::toProto);
    putMap(dto.getSignals(), builder::putSignals, DefinitionsProtoMapper::toProto);
    return builder.build();
  }

  public static ParsedDefinitionsDTO toDto(ParsedDefinitionsMessage message) {
    if (message == null) {
      return null;
    }
    return new ParsedDefinitionsDTO(
        message.hasDefinitionsKey() ? toDto(message.getDefinitionsKey()) : null,
        message.hasRootProcess() ? toDto(message.getRootProcess()) : null,
        toDtoMap(message.getMessagesMap(), DefinitionsProtoMapper::toDto),
        toDtoMap(message.getEscalationsMap(), DefinitionsProtoMapper::toDto),
        toDtoMap(message.getErrorsMap(), DefinitionsProtoMapper::toDto),
        toDtoMap(message.getSignalsMap(), DefinitionsProtoMapper::toDto));
  }

  public static ProcessDefinitionMessage toProto(ProcessDefinitionDTO dto) {
    ProcessDefinitionMessage.Builder builder = ProcessDefinitionMessage.newBuilder();
    if (dto == null) {
      return builder.build();
    }
    if (dto.getDefinitions() != null) {
      builder.setDefinitions(toProto(dto.getDefinitions()));
    }
    if (dto.getVersion() != null) {
      builder.setVersion(dto.getVersion());
    }
    if (dto.getState() != null) {
      builder.setState(toProto(dto.getState()));
    }
    return builder.build();
  }

  public static ProcessDefinitionDTO toDto(ProcessDefinitionMessage message) {
    if (message == null) {
      return null;
    }
    return new ProcessDefinitionDTO(
        message.hasDefinitions() ? toDto(message.getDefinitions()) : null,
        message.getVersion(),
        toDto(message.getState()));
  }

  public static BaseElementEnvelope toProto(BaseElementDTO dto) {
    BaseElementEnvelope.Builder builder = BaseElementEnvelope.newBuilder();
    if (dto == null) {
      return builder.build();
    }
    if (dto instanceof BoundaryEventDTO boundaryEvent) {
      builder.setBoundaryEvent(toProto(boundaryEvent));
    } else if (dto instanceof StartEventDTO startEvent) {
      builder.setStartEvent(toProto(startEvent));
    } else if (dto instanceof IntermediateCatchEventDTO catchEvent) {
      builder.setCatchEvent(toProto(catchEvent));
    } else if (dto instanceof IntermediateThrowEventDTO throwEvent) {
      builder.setThrowEvent(toProto(throwEvent));
    } else if (dto instanceof EndEventDTO endEvent) {
      builder.setEndEvent(toProto(endEvent));
    } else if (dto instanceof InclusiveGatewayDTO inclusiveGateway) {
      builder.setInclusiveGw(toProto(inclusiveGateway));
    } else if (dto instanceof EventBasedGatewayDTO eventBasedGateway) {
      builder.setEventBasedGw(toProto(eventBasedGateway));
    } else if (dto instanceof ParallelGatewayDTO parallelGateway) {
      builder.setParallelGw(toProto(parallelGateway));
    } else if (dto instanceof ExclusiveGatewayDTO exclusiveGateway) {
      builder.setExclusiveGw(toProto(exclusiveGateway));
    } else if (dto instanceof AdHocSubProcessDTO adHocSubProcess) {
      builder.setAdHocSubProcess(toProto(adHocSubProcess));
    } else if (dto instanceof SubProcessDTO subProcess) {
      builder.setSubProcess(toProto(subProcess));
    } else if (dto instanceof CallActivityDTO callActivity) {
      builder.setCallActivity(toProto(callActivity));
    } else if (dto instanceof ReceiveTaskDTO receiveTask) {
      builder.setReceiveTask(toProto(receiveTask));
    } else if (dto instanceof SendTaskDTO sendTask) {
      builder.setSendTask(toProto(sendTask));
    } else if (dto instanceof ServiceTaskDTO serviceTask) {
      builder.setServiceTask(toProto(serviceTask));
    } else if (dto instanceof MessageEndEventDTO messageEndEvent) {
      builder.setMsgEndEvent(toProto(messageEndEvent));
    } else if (dto instanceof MessageIntermediateThrowEventDTO messageThrowEvent) {
      builder.setMsgThrowEvent(toProto(messageThrowEvent));
    } else if (dto instanceof BusinessRuleTaskDTO businessRuleTask) {
      builder.setBrt(toProto(businessRuleTask));
    } else if (dto instanceof ScriptTaskDTO scriptTask) {
      builder.setScriptTask(toProto(scriptTask));
    } else if (dto instanceof UserTaskDTO userTask) {
      builder.setUserTask(toProto(userTask));
    } else if (dto instanceof TaskDTO task) {
      builder.setTask(toProto(task));
    } else if (dto instanceof SequenceFlowDTO sequenceFlow) {
      builder.setSequenceFlow(toProto(sequenceFlow));
    } else if (dto instanceof ProcessDTO process) {
      builder.setProcess(toProto(process));
    } else if (dto instanceof LinkEventDefinitionDTO linkEventDefinition) {
      builder.setLinkEventDef(toProto(linkEventDefinition));
    } else if (dto instanceof TerminateEventDefinitionDTO terminateEventDefinition) {
      builder.setTerminateEventDef(toProto(terminateEventDefinition));
    } else if (dto instanceof EscalationEventDefinitionDTO escalationEventDefinition) {
      builder.setEscalationEventDef(toProto(escalationEventDefinition));
    } else if (dto instanceof TimerEventDefinitionDTO timerEventDefinition) {
      builder.setTimerEventDef(toProto(timerEventDefinition));
    } else if (dto instanceof ErrorEventDefinitionDTO errorEventDefinition) {
      builder.setErrorEventDef(toProto(errorEventDefinition));
    } else if (dto instanceof MessageEventDefinitionDTO messageEventDefinition) {
      builder.setMsgEventDef(toProto(messageEventDefinition));
    } else if (dto instanceof SignalEventDefinitionDTO signalEventDefinition) {
      builder.setSignalEventDef(toProto(signalEventDefinition));
    } else if (dto instanceof CompensationEventDefinitionDTO compensationEventDefinition) {
      builder.setCompensationEventDef(toProto(compensationEventDefinition));
    } else {
      throw new IllegalArgumentException(
          "Unsupported BPMN element type: " + dto.getClass().getName());
    }
    return builder.build();
  }

  public static BaseElementDTO toDto(BaseElementEnvelope envelope) {
    if (envelope == null) {
      return null;
    }
    return switch (envelope.getElementCase()) {
      case BOUNDARY_EVENT -> toDto(envelope.getBoundaryEvent());
      case START_EVENT -> toDto(envelope.getStartEvent());
      case CATCH_EVENT -> toDto(envelope.getCatchEvent());
      case THROW_EVENT -> toDto(envelope.getThrowEvent());
      case END_EVENT -> toDto(envelope.getEndEvent());
      case INCLUSIVE_GW -> toDto(envelope.getInclusiveGw());
      case EVENT_BASED_GW -> toDto(envelope.getEventBasedGw());
      case PARALLEL_GW -> toDto(envelope.getParallelGw());
      case EXCLUSIVE_GW -> toDto(envelope.getExclusiveGw());
      case SUB_PROCESS -> toDto(envelope.getSubProcess());
      case AD_HOC_SUB_PROCESS -> toDto(envelope.getAdHocSubProcess());
      case CALL_ACTIVITY -> toDto(envelope.getCallActivity());
      case RECEIVE_TASK -> toDto(envelope.getReceiveTask());
      case SEND_TASK -> toDto(envelope.getSendTask());
      case SERVICE_TASK -> toDto(envelope.getServiceTask());
      case MSG_END_EVENT -> toDto(envelope.getMsgEndEvent());
      case MSG_THROW_EVENT -> toDto(envelope.getMsgThrowEvent());
      case BRT -> toDto(envelope.getBrt());
      case SCRIPT_TASK -> toDto(envelope.getScriptTask());
      case USER_TASK -> toDto(envelope.getUserTask());
      case TASK -> toDto(envelope.getTask());
      case SEQUENCE_FLOW -> toDto(envelope.getSequenceFlow());
      case PROCESS -> toDto(envelope.getProcess());
      case LINK_EVENT_DEF -> toDto(envelope.getLinkEventDef());
      case TERMINATE_EVENT_DEF -> toDto(envelope.getTerminateEventDef());
      case ESCALATION_EVENT_DEF -> toDto(envelope.getEscalationEventDef());
      case TIMER_EVENT_DEF -> toDto(envelope.getTimerEventDef());
      case ERROR_EVENT_DEF -> toDto(envelope.getErrorEventDef());
      case MSG_EVENT_DEF -> toDto(envelope.getMsgEventDef());
      case SIGNAL_EVENT_DEF -> toDto(envelope.getSignalEventDef());
      case COMPENSATION_EVENT_DEF -> toDto(envelope.getCompensationEventDef());
      case ELEMENT_NOT_SET -> null;
    };
  }

  private static XmlDefinitionsMessage toProto(XmlDefinitionsDTO dto) {
    XmlDefinitionsMessage.Builder builder = XmlDefinitionsMessage.newBuilder();
    if (dto != null && dto.getXml() != null) {
      builder.setXml(dto.getXml());
    }
    return builder.build();
  }

  private static XmlDefinitionsDTO toDto(XmlDefinitionsMessage message) {
    return new XmlDefinitionsDTO(emptyToNull(message.getXml()));
  }

  private static ProcessDefinitionActivationMessage toProto(ProcessDefinitionActivationDTO dto) {
    ProcessDefinitionActivationMessage.Builder builder =
        ProcessDefinitionActivationMessage.newBuilder();
    if (dto == null) {
      return builder.build();
    }
    if (dto.getProcessDefinitionKey() != null) {
      builder.setProcessDefinitionKey(toProto(dto.getProcessDefinitionKey()));
    }
    if (dto.getState() != null) {
      builder.setState(toProto(dto.getState()));
    }
    return builder.build();
  }

  private static ProcessDefinitionActivationDTO toDto(ProcessDefinitionActivationMessage message) {
    return new ProcessDefinitionActivationDTO(
        message.hasProcessDefinitionKey() ? toDto(message.getProcessDefinitionKey()) : null,
        toDto(message.getState()));
  }

  private static ProcessMessage toProto(ProcessDTO dto) {
    ProcessMessage.Builder builder = ProcessMessage.newBuilder();
    if (dto == null) {
      return builder.build();
    }
    setBaseElementFields(builder::setId, builder::setParentId, dto);
    if (dto.getVersionTag() != null) {
      builder.setVersionTag(dto.getVersionTag());
    }
    if (dto.getFlowElements() != null) {
      builder.setFlowElements(toProto(dto.getFlowElements()));
    }
    return builder.build();
  }

  private static ProcessDTO toDto(ProcessMessage message) {
    return new ProcessDTO(
        emptyToNull(message.getId()),
        emptyToNull(message.getParentId()),
        emptyToNull(message.getVersionTag()),
        message.hasFlowElements() ? toDto(message.getFlowElements()) : null);
  }

  private static FlowElementsMessage toProto(FlowElementsDTO dto) {
    FlowElementsMessage.Builder builder = FlowElementsMessage.newBuilder();
    if (dto == null || dto.getElements() == null) {
      return builder.build();
    }
    dto.getElements().forEach((key, value) -> builder.putElements(key, toProto(value)));
    return builder.build();
  }

  private static FlowElementsDTO toDto(FlowElementsMessage message) {
    Map<String, FlowElementDTO> elements = new LinkedHashMap<>();
    message
        .getElementsMap()
        .forEach(
            (key, value) -> {
              BaseElementDTO dto = toDto(value);
              if (!(dto instanceof FlowElementDTO flowElement)) {
                throw new IllegalArgumentException(
                    "FlowElementsMessage contained non-flow element type for key '" + key + "'");
              }
              elements.put(key, flowElement);
            });
    return new FlowElementsDTO(elements);
  }

  private static BoundaryEventMessage toProto(BoundaryEventDTO dto) {
    BoundaryEventMessage.Builder builder = BoundaryEventMessage.newBuilder();
    if (dto == null) {
      return builder.build();
    }
    setFlowNodeFields(
        builder::setId,
        builder::setParentId,
        builder::setName,
        builder::addAllIncoming,
        builder::addAllOutgoing,
        dto);
    if (dto.getIoMapping() != null) {
      builder.setIoMapping(toProto(dto.getIoMapping()));
    }
    addEventDefinitions(builder::addEventDefinitions, dto.getEventDefinitions());
    if (dto.getAttachedToRef() != null) {
      builder.setAttachedToRef(dto.getAttachedToRef());
    }
    builder.setCancelActivity(dto.isCancelActivity());
    if (dto.getCompensationHandlerId() != null) {
      builder.setCompensationHandlerId(dto.getCompensationHandlerId());
    }
    return builder.build();
  }

  private static BoundaryEventDTO toDto(BoundaryEventMessage message) {
    return new BoundaryEventDTO(
        emptyToNull(message.getId()),
        emptyToNull(message.getParentId()),
        emptyToNull(message.getName()),
        stringSet(message.getIncomingList()),
        stringSet(message.getOutgoingList()),
        toEventDefinitionSet(message.getEventDefinitionsList()),
        emptyToNull(message.getAttachedToRef()),
        message.getCancelActivity(),
        message.hasIoMapping() ? toDto(message.getIoMapping()) : null,
        emptyToNull(message.getCompensationHandlerId()));
  }

  private static StartEventMessage toProto(StartEventDTO dto) {
    StartEventMessage.Builder builder = StartEventMessage.newBuilder();
    if (dto == null) {
      return builder.build();
    }
    setFlowNodeFields(
        builder::setId,
        builder::setParentId,
        builder::setName,
        builder::addAllIncoming,
        builder::addAllOutgoing,
        dto);
    if (dto.getIoMapping() != null) {
      builder.setIoMapping(toProto(dto.getIoMapping()));
    }
    addEventDefinitions(builder::addEventDefinitions, dto.getEventDefinitions());
    builder.setInterrupting(dto.isInterrupting());
    return builder.build();
  }

  private static StartEventDTO toDto(StartEventMessage message) {
    return new StartEventDTO(
        emptyToNull(message.getId()),
        emptyToNull(message.getParentId()),
        emptyToNull(message.getName()),
        stringSet(message.getIncomingList()),
        stringSet(message.getOutgoingList()),
        toEventDefinitionSet(message.getEventDefinitionsList()),
        message.hasIoMapping() ? toDto(message.getIoMapping()) : null,
        message.getInterrupting());
  }

  private static IntermediateCatchEventMessage toProto(IntermediateCatchEventDTO dto) {
    IntermediateCatchEventMessage.Builder builder = IntermediateCatchEventMessage.newBuilder();
    if (dto == null) {
      return builder.build();
    }
    setFlowNodeFields(
        builder::setId,
        builder::setParentId,
        builder::setName,
        builder::addAllIncoming,
        builder::addAllOutgoing,
        dto);
    if (dto.getIoMapping() != null) {
      builder.setIoMapping(toProto(dto.getIoMapping()));
    }
    addEventDefinitions(builder::addEventDefinitions, dto.getEventDefinitions());
    return builder.build();
  }

  private static IntermediateCatchEventDTO toDto(IntermediateCatchEventMessage message) {
    return new IntermediateCatchEventDTO(
        emptyToNull(message.getId()),
        emptyToNull(message.getParentId()),
        emptyToNull(message.getName()),
        stringSet(message.getIncomingList()),
        stringSet(message.getOutgoingList()),
        toEventDefinitionSet(message.getEventDefinitionsList()),
        message.hasIoMapping() ? toDto(message.getIoMapping()) : null);
  }

  private static IntermediateThrowEventMessage toProto(IntermediateThrowEventDTO dto) {
    IntermediateThrowEventMessage.Builder builder = IntermediateThrowEventMessage.newBuilder();
    if (dto == null) {
      return builder.build();
    }
    setFlowNodeFields(
        builder::setId,
        builder::setParentId,
        builder::setName,
        builder::addAllIncoming,
        builder::addAllOutgoing,
        dto);
    if (dto.getIoMapping() != null) {
      builder.setIoMapping(toProto(dto.getIoMapping()));
    }
    addEventDefinitions(builder::addEventDefinitions, dto.getEventDefinitions());
    return builder.build();
  }

  private static IntermediateThrowEventDTO toDto(IntermediateThrowEventMessage message) {
    return new IntermediateThrowEventDTO(
        emptyToNull(message.getId()),
        emptyToNull(message.getParentId()),
        emptyToNull(message.getName()),
        stringSet(message.getIncomingList()),
        stringSet(message.getOutgoingList()),
        message.hasIoMapping() ? toDto(message.getIoMapping()) : null,
        toEventDefinitionSet(message.getEventDefinitionsList()));
  }

  private static EndEventMessage toProto(EndEventDTO dto) {
    EndEventMessage.Builder builder = EndEventMessage.newBuilder();
    if (dto == null) {
      return builder.build();
    }
    setFlowNodeFields(
        builder::setId,
        builder::setParentId,
        builder::setName,
        builder::addAllIncoming,
        builder::addAllOutgoing,
        dto);
    if (dto.getIoMapping() != null) {
      builder.setIoMapping(toProto(dto.getIoMapping()));
    }
    addEventDefinitions(builder::addEventDefinitions, dto.getEventDefinitions());
    return builder.build();
  }

  private static EndEventDTO toDto(EndEventMessage message) {
    return new EndEventDTO(
        emptyToNull(message.getId()),
        emptyToNull(message.getParentId()),
        emptyToNull(message.getName()),
        stringSet(message.getIncomingList()),
        stringSet(message.getOutgoingList()),
        message.hasIoMapping() ? toDto(message.getIoMapping()) : null,
        toEventDefinitionSet(message.getEventDefinitionsList()));
  }

  private static InclusiveGatewayMessage toProto(InclusiveGatewayDTO dto) {
    InclusiveGatewayMessage.Builder builder = InclusiveGatewayMessage.newBuilder();
    if (dto == null) {
      return builder.build();
    }
    setGatewayFields(
        builder::setId,
        builder::setParentId,
        builder::setName,
        builder::addAllIncoming,
        builder::addAllOutgoing,
        builder::setDefaultFlow,
        dto);
    return builder.build();
  }

  private static InclusiveGatewayDTO toDto(InclusiveGatewayMessage message) {
    return new InclusiveGatewayDTO(
        emptyToNull(message.getId()),
        emptyToNull(message.getParentId()),
        emptyToNull(message.getName()),
        stringSet(message.getIncomingList()),
        stringSet(message.getOutgoingList()),
        emptyToNull(message.getDefaultFlow()));
  }

  private static EventBasedGatewayMessage toProto(EventBasedGatewayDTO dto) {
    EventBasedGatewayMessage.Builder builder = EventBasedGatewayMessage.newBuilder();
    if (dto == null) {
      return builder.build();
    }
    setGatewayFields(
        builder::setId,
        builder::setParentId,
        builder::setName,
        builder::addAllIncoming,
        builder::addAllOutgoing,
        builder::setDefaultFlow,
        dto);
    return builder.build();
  }

  private static EventBasedGatewayDTO toDto(EventBasedGatewayMessage message) {
    return new EventBasedGatewayDTO(
        emptyToNull(message.getId()),
        emptyToNull(message.getParentId()),
        emptyToNull(message.getName()),
        stringSet(message.getIncomingList()),
        stringSet(message.getOutgoingList()),
        emptyToNull(message.getDefaultFlow()));
  }

  private static ParallelGatewayMessage toProto(ParallelGatewayDTO dto) {
    ParallelGatewayMessage.Builder builder = ParallelGatewayMessage.newBuilder();
    if (dto == null) {
      return builder.build();
    }
    setFlowNodeFields(
        builder::setId,
        builder::setParentId,
        builder::setName,
        builder::addAllIncoming,
        builder::addAllOutgoing,
        dto);
    if (dto.getDefaultFlow() != null) {
      builder.setDefaultFlow(dto.getDefaultFlow());
    }
    return builder.build();
  }

  private static ParallelGatewayDTO toDto(ParallelGatewayMessage message) {
    ParallelGatewayDTO dto =
        new ParallelGatewayDTO(
            emptyToNull(message.getId()),
            emptyToNull(message.getParentId()),
            emptyToNull(message.getName()),
            stringSet(message.getIncomingList()),
            stringSet(message.getOutgoingList()));
    if (!message.getDefaultFlow().isEmpty()) {
      dto.setDefaultFlow(message.getDefaultFlow());
    }
    return dto;
  }

  private static ExclusiveGatewayMessage toProto(ExclusiveGatewayDTO dto) {
    ExclusiveGatewayMessage.Builder builder = ExclusiveGatewayMessage.newBuilder();
    if (dto == null) {
      return builder.build();
    }
    setGatewayFields(
        builder::setId,
        builder::setParentId,
        builder::setName,
        builder::addAllIncoming,
        builder::addAllOutgoing,
        builder::setDefaultFlow,
        dto);
    return builder.build();
  }

  private static ExclusiveGatewayDTO toDto(ExclusiveGatewayMessage message) {
    return new ExclusiveGatewayDTO(
        emptyToNull(message.getId()),
        emptyToNull(message.getParentId()),
        emptyToNull(message.getName()),
        stringSet(message.getIncomingList()),
        stringSet(message.getOutgoingList()),
        emptyToNull(message.getDefaultFlow()));
  }

  private static AdHocSubProcessMessage toProto(AdHocSubProcessDTO dto) {
    AdHocSubProcessMessage.Builder builder = AdHocSubProcessMessage.newBuilder();
    if (dto == null) {
      return builder.build();
    }
    setActivityFields(
        builder::setId,
        builder::setParentId,
        builder::setName,
        builder::addAllIncoming,
        builder::addAllOutgoing,
        builder::setLoopCharacteristics,
        builder::setIoMapping,
        dto);
    if (dto.getElements() != null) {
      builder.setElements(toProto(dto.getElements()));
    }
    if (dto.getActiveElementsCollection() != null) {
      builder.setActiveElementsCollection(dto.getActiveElementsCollection());
    }
    if (dto.getCompletionCondition() != null) {
      builder.setCompletionCondition(dto.getCompletionCondition());
    }
    builder.setCancelRemainingInstances(dto.isCancelRemainingInstances());
    return builder.build();
  }

  private static AdHocSubProcessDTO toDto(AdHocSubProcessMessage message) {
    return new AdHocSubProcessDTO(
        emptyToNull(message.getId()),
        emptyToNull(message.getParentId()),
        emptyToNull(message.getName()),
        stringSet(message.getIncomingList()),
        stringSet(message.getOutgoingList()),
        message.hasLoopCharacteristics() ? toDto(message.getLoopCharacteristics()) : null,
        message.hasElements() ? toDto(message.getElements()) : null,
        message.hasIoMapping() ? toDto(message.getIoMapping()) : null,
        emptyToNull(message.getActiveElementsCollection()),
        emptyToNull(message.getCompletionCondition()),
        message.getCancelRemainingInstances());
  }

  private static SubProcessMessage toProto(SubProcessDTO dto) {
    SubProcessMessage.Builder builder = SubProcessMessage.newBuilder();
    if (dto == null) {
      return builder.build();
    }
    setActivityFields(
        builder::setId,
        builder::setParentId,
        builder::setName,
        builder::addAllIncoming,
        builder::addAllOutgoing,
        builder::setLoopCharacteristics,
        builder::setIoMapping,
        dto);
    if (dto.getElements() != null) {
      builder.setElements(toProto(dto.getElements()));
    }
    builder.setTriggeredByEvent(dto.isTriggeredByEvent());
    builder.setIsForCompensation(dto.isForCompensation());
    return builder.build();
  }

  private static SubProcessDTO toDto(SubProcessMessage message) {
    SubProcessDTO dto =
        new SubProcessDTO(
            emptyToNull(message.getId()),
            emptyToNull(message.getParentId()),
            emptyToNull(message.getName()),
            stringSet(message.getIncomingList()),
            stringSet(message.getOutgoingList()),
            message.hasLoopCharacteristics() ? toDto(message.getLoopCharacteristics()) : null,
            message.hasElements() ? toDto(message.getElements()) : null,
            message.hasIoMapping() ? toDto(message.getIoMapping()) : null,
            message.getTriggeredByEvent());
    dto.setForCompensation(message.getIsForCompensation());
    return dto;
  }

  private static CallActivityMessage toProto(CallActivityDTO dto) {
    CallActivityMessage.Builder builder = CallActivityMessage.newBuilder();
    if (dto == null) {
      return builder.build();
    }
    setActivityFields(
        builder::setId,
        builder::setParentId,
        builder::setName,
        builder::addAllIncoming,
        builder::addAllOutgoing,
        builder::setLoopCharacteristics,
        builder::setIoMapping,
        dto);
    if (dto.getCalledElement() != null) {
      builder.setCalledElement(dto.getCalledElement());
    }
    builder.setPropagateAllParentVariables(dto.isPropagateAllParentVariables());
    builder.setPropagateAllChildVariables(dto.isPropagateAllChildVariables());
    builder.setIsForCompensation(dto.isForCompensation());
    return builder.build();
  }

  private static CallActivityDTO toDto(CallActivityMessage message) {
    CallActivityDTO dto =
        new CallActivityDTO(
            emptyToNull(message.getId()),
            emptyToNull(message.getParentId()),
            emptyToNull(message.getName()),
            stringSet(message.getIncomingList()),
            stringSet(message.getOutgoingList()),
            message.hasLoopCharacteristics() ? toDto(message.getLoopCharacteristics()) : null,
            emptyToNull(message.getCalledElement()),
            message.getPropagateAllParentVariables(),
            message.getPropagateAllChildVariables(),
            message.hasIoMapping() ? toDto(message.getIoMapping()) : null);
    dto.setForCompensation(message.getIsForCompensation());
    return dto;
  }

  private static ReceiveTaskMessage toProto(ReceiveTaskDTO dto) {
    ReceiveTaskMessage.Builder builder = ReceiveTaskMessage.newBuilder();
    if (dto == null) {
      return builder.build();
    }
    setActivityFields(
        builder::setId,
        builder::setParentId,
        builder::setName,
        builder::addAllIncoming,
        builder::addAllOutgoing,
        builder::setLoopCharacteristics,
        builder::setIoMapping,
        dto);
    if (dto.getMessageRef() != null) {
      builder.setMessageRef(dto.getMessageRef());
    }
    return builder.build();
  }

  private static ReceiveTaskDTO toDto(ReceiveTaskMessage message) {
    return new ReceiveTaskDTO(
        emptyToNull(message.getId()),
        emptyToNull(message.getParentId()),
        emptyToNull(message.getName()),
        stringSet(message.getIncomingList()),
        stringSet(message.getOutgoingList()),
        message.hasLoopCharacteristics() ? toDto(message.getLoopCharacteristics()) : null,
        emptyToNull(message.getMessageRef()),
        message.hasIoMapping() ? toDto(message.getIoMapping()) : null);
  }

  private static SendTaskMessage toProto(SendTaskDTO dto) {
    SendTaskMessage.Builder builder = SendTaskMessage.newBuilder();
    if (dto == null) {
      return builder.build();
    }
    setExternalTaskFields(
        builder::setId,
        builder::setParentId,
        builder::setName,
        builder::addAllIncoming,
        builder::addAllOutgoing,
        builder::setLoopCharacteristics,
        builder::setIoMapping,
        builder::setWorkerDefinition,
        builder::setRetries,
        builder::setImplementation,
        builder::putAllHeaders,
        dto);
    builder.setIsForCompensation(dto.isForCompensation());
    return builder.build();
  }

  private static SendTaskDTO toDto(SendTaskMessage message) {
    SendTaskDTO dto =
        new SendTaskDTO(
            emptyToNull(message.getId()),
            emptyToNull(message.getParentId()),
            emptyToNull(message.getName()),
            emptyToNull(message.getWorkerDefinition()),
            emptyToNull(message.getRetries()),
            stringSet(message.getIncomingList()),
            stringSet(message.getOutgoingList()),
            emptyToNull(message.getImplementation()),
            message.hasLoopCharacteristics() ? toDto(message.getLoopCharacteristics()) : null,
            new LinkedHashMap<>(message.getHeadersMap()),
            message.hasIoMapping() ? toDto(message.getIoMapping()) : null);
    dto.setForCompensation(message.getIsForCompensation());
    return dto;
  }

  private static ServiceTaskMessage toProto(ServiceTaskDTO dto) {
    ServiceTaskMessage.Builder builder = ServiceTaskMessage.newBuilder();
    if (dto == null) {
      return builder.build();
    }
    setExternalTaskFields(
        builder::setId,
        builder::setParentId,
        builder::setName,
        builder::addAllIncoming,
        builder::addAllOutgoing,
        builder::setLoopCharacteristics,
        builder::setIoMapping,
        builder::setWorkerDefinition,
        builder::setRetries,
        builder::setImplementation,
        builder::putAllHeaders,
        dto);
    builder.setIsForCompensation(dto.isForCompensation());
    return builder.build();
  }

  private static ServiceTaskDTO toDto(ServiceTaskMessage message) {
    ServiceTaskDTO dto =
        new ServiceTaskDTO(
            emptyToNull(message.getId()),
            emptyToNull(message.getParentId()),
            emptyToNull(message.getName()),
            emptyToNull(message.getWorkerDefinition()),
            emptyToNull(message.getRetries()),
            stringSet(message.getIncomingList()),
            stringSet(message.getOutgoingList()),
            emptyToNull(message.getImplementation()),
            message.hasLoopCharacteristics() ? toDto(message.getLoopCharacteristics()) : null,
            new LinkedHashMap<>(message.getHeadersMap()),
            message.hasIoMapping() ? toDto(message.getIoMapping()) : null);
    dto.setForCompensation(message.getIsForCompensation());
    return dto;
  }

  private static MessageEndEventMessage toProto(MessageEndEventDTO dto) {
    MessageEndEventMessage.Builder builder = MessageEndEventMessage.newBuilder();
    if (dto == null) {
      return builder.build();
    }
    setExternalTaskFields(
        builder::setId,
        builder::setParentId,
        builder::setName,
        builder::addAllIncoming,
        builder::addAllOutgoing,
        builder::setLoopCharacteristics,
        builder::setIoMapping,
        builder::setWorkerDefinition,
        builder::setRetries,
        builder::setImplementation,
        builder::putAllHeaders,
        dto);
    return builder.build();
  }

  private static MessageEndEventDTO toDto(MessageEndEventMessage message) {
    return new MessageEndEventDTO(
        emptyToNull(message.getId()),
        emptyToNull(message.getParentId()),
        emptyToNull(message.getName()),
        emptyToNull(message.getWorkerDefinition()),
        emptyToNull(message.getRetries()),
        stringSet(message.getIncomingList()),
        stringSet(message.getOutgoingList()),
        new LinkedHashMap<>(message.getHeadersMap()),
        message.hasIoMapping() ? toDto(message.getIoMapping()) : null);
  }

  private static MessageIntermediateThrowEventMessage toProto(
      MessageIntermediateThrowEventDTO dto) {
    MessageIntermediateThrowEventMessage.Builder builder =
        MessageIntermediateThrowEventMessage.newBuilder();
    if (dto == null) {
      return builder.build();
    }
    setExternalTaskFields(
        builder::setId,
        builder::setParentId,
        builder::setName,
        builder::addAllIncoming,
        builder::addAllOutgoing,
        builder::setLoopCharacteristics,
        builder::setIoMapping,
        builder::setWorkerDefinition,
        builder::setRetries,
        builder::setImplementation,
        builder::putAllHeaders,
        dto);
    return builder.build();
  }

  private static MessageIntermediateThrowEventDTO toDto(
      MessageIntermediateThrowEventMessage message) {
    return new MessageIntermediateThrowEventDTO(
        emptyToNull(message.getId()),
        emptyToNull(message.getParentId()),
        emptyToNull(message.getName()),
        emptyToNull(message.getWorkerDefinition()),
        emptyToNull(message.getRetries()),
        stringSet(message.getIncomingList()),
        stringSet(message.getOutgoingList()),
        new LinkedHashMap<>(message.getHeadersMap()),
        message.hasIoMapping() ? toDto(message.getIoMapping()) : null);
  }

  private static BusinessRuleTaskMessage toProto(BusinessRuleTaskDTO dto) {
    BusinessRuleTaskMessage.Builder builder = BusinessRuleTaskMessage.newBuilder();
    if (dto == null) {
      return builder.build();
    }
    setActivityFields(
        builder::setId,
        builder::setParentId,
        builder::setName,
        builder::addAllIncoming,
        builder::addAllOutgoing,
        builder::setLoopCharacteristics,
        builder::setIoMapping,
        dto);
    if (dto.getDecisionId() != null) {
      builder.setDecisionId(dto.getDecisionId());
    }
    if (dto.getResultVariable() != null) {
      builder.setResultVariable(dto.getResultVariable());
    }
    return builder.build();
  }

  private static BusinessRuleTaskDTO toDto(BusinessRuleTaskMessage message) {
    return new BusinessRuleTaskDTO(
        emptyToNull(message.getId()),
        emptyToNull(message.getParentId()),
        emptyToNull(message.getName()),
        stringSet(message.getIncomingList()),
        stringSet(message.getOutgoingList()),
        message.hasLoopCharacteristics() ? toDto(message.getLoopCharacteristics()) : null,
        message.hasIoMapping() ? toDto(message.getIoMapping()) : null,
        emptyToNull(message.getDecisionId()),
        emptyToNull(message.getResultVariable()));
  }

  private static ScriptTaskMessage toProto(ScriptTaskDTO dto) {
    ScriptTaskMessage.Builder builder = ScriptTaskMessage.newBuilder();
    if (dto == null) {
      return builder.build();
    }
    setExternalTaskFields(
        builder::setId,
        builder::setParentId,
        builder::setName,
        builder::addAllIncoming,
        builder::addAllOutgoing,
        builder::setLoopCharacteristics,
        builder::setIoMapping,
        builder::setWorkerDefinition,
        builder::setRetries,
        builder::setImplementation,
        builder::putAllHeaders,
        dto);
    builder.setScriptType(toProto(dto.getScriptType()));
    if (dto.getScriptExpressions() != null) {
      builder.addAllScriptExpressions(dto.getScriptExpressions());
    }
    if (dto.getResultVariableName() != null) {
      builder.setResultVariableName(dto.getResultVariableName());
    }
    builder.setIsForCompensation(dto.isForCompensation());
    return builder.build();
  }

  private static ScriptTaskDTO toDto(ScriptTaskMessage message) {
    ScriptTaskDTO dto =
        new ScriptTaskDTO(
            emptyToNull(message.getId()),
            emptyToNull(message.getParentId()),
            emptyToNull(message.getName()),
            emptyToNull(message.getWorkerDefinition()),
            emptyToNull(message.getRetries()),
            stringSet(message.getIncomingList()),
            stringSet(message.getOutgoingList()),
            message.hasLoopCharacteristics() ? toDto(message.getLoopCharacteristics()) : null,
            new LinkedHashMap<>(message.getHeadersMap()),
            message.hasIoMapping() ? toDto(message.getIoMapping()) : null,
            toDto(message.getScriptType()),
            List.copyOf(message.getScriptExpressionsList()),
            emptyToNull(message.getResultVariableName()));
    dto.setForCompensation(message.getIsForCompensation());
    return dto;
  }

  private static UserTaskMessage toProto(UserTaskDTO dto) {
    UserTaskMessage.Builder builder = UserTaskMessage.newBuilder();
    if (dto == null) {
      return builder.build();
    }
    setActivityFields(
        builder::setId,
        builder::setParentId,
        builder::setName,
        builder::addAllIncoming,
        builder::addAllOutgoing,
        builder::setLoopCharacteristics,
        builder::setIoMapping,
        dto);
    builder.setUserTaskType(toProto(dto.getUserTaskType()));
    if (dto.getAssignmentDefinition() != null) {
      builder.setAssignmentDefinition(toProto(dto.getAssignmentDefinition()));
    }
    if (dto.getTaskSchedule() != null) {
      builder.setTaskSchedule(toProto(dto.getTaskSchedule()));
    }
    if (dto.getPriorityDefinition() != null) {
      builder.setPriorityDefinition(toProto(dto.getPriorityDefinition()));
    }
    if (dto.getHeaders() != null) {
      builder.putAllHeaders(dto.getHeaders());
    }
    builder.setIsForCompensation(dto.isForCompensation());
    return builder.build();
  }

  private static UserTaskDTO toDto(UserTaskMessage message) {
    UserTaskDTO dto =
        new UserTaskDTO(
            emptyToNull(message.getId()),
            emptyToNull(message.getParentId()),
            emptyToNull(message.getName()),
            stringSet(message.getIncomingList()),
            stringSet(message.getOutgoingList()),
            message.hasLoopCharacteristics() ? toDto(message.getLoopCharacteristics()) : null,
            message.hasIoMapping() ? toDto(message.getIoMapping()) : null,
            new LinkedHashMap<>(message.getHeadersMap()),
            toDto(message.getUserTaskType()),
            message.hasAssignmentDefinition() ? toDto(message.getAssignmentDefinition()) : null,
            message.hasTaskSchedule() ? toDto(message.getTaskSchedule()) : null,
            message.hasPriorityDefinition() ? toDto(message.getPriorityDefinition()) : null);
    dto.setForCompensation(message.getIsForCompensation());
    return dto;
  }

  private static TaskMessage toProto(TaskDTO dto) {
    TaskMessage.Builder builder = TaskMessage.newBuilder();
    if (dto == null) {
      return builder.build();
    }
    setActivityFields(
        builder::setId,
        builder::setParentId,
        builder::setName,
        builder::addAllIncoming,
        builder::addAllOutgoing,
        builder::setLoopCharacteristics,
        builder::setIoMapping,
        dto);
    builder.setIsForCompensation(dto.isForCompensation());
    return builder.build();
  }

  private static TaskDTO toDto(TaskMessage message) {
    TaskDTO dto =
        new TaskDTO(
            emptyToNull(message.getId()),
            emptyToNull(message.getParentId()),
            emptyToNull(message.getName()),
            stringSet(message.getIncomingList()),
            stringSet(message.getOutgoingList()),
            message.hasLoopCharacteristics() ? toDto(message.getLoopCharacteristics()) : null,
            message.hasIoMapping() ? toDto(message.getIoMapping()) : null);
    dto.setForCompensation(message.getIsForCompensation());
    return dto;
  }

  private static SequenceFlowMessage toProto(SequenceFlowDTO dto) {
    SequenceFlowMessage.Builder builder = SequenceFlowMessage.newBuilder();
    if (dto == null) {
      return builder.build();
    }
    setBaseElementFields(builder::setId, builder::setParentId, dto);
    if (dto.getName() != null) {
      builder.setName(dto.getName());
    }
    if (dto.getSource() != null) {
      builder.setSource(dto.getSource());
    }
    if (dto.getTarget() != null) {
      builder.setTarget(dto.getTarget());
    }
    if (dto.getCondition() != null) {
      builder.setCondition(toProto(dto.getCondition()));
    }
    return builder.build();
  }

  private static SequenceFlowDTO toDto(SequenceFlowMessage message) {
    return new SequenceFlowDTO(
        emptyToNull(message.getId()),
        emptyToNull(message.getParentId()),
        emptyToNull(message.getName()),
        emptyToNull(message.getSource()),
        emptyToNull(message.getTarget()),
        message.hasCondition() ? toDto(message.getCondition()) : null);
  }

  private static LinkEventDefinitionMessage toProto(LinkEventDefinitionDTO dto) {
    LinkEventDefinitionMessage.Builder builder = LinkEventDefinitionMessage.newBuilder();
    if (dto == null) {
      return builder.build();
    }
    setBaseElementFields(builder::setId, builder::setParentId, dto);
    if (dto.getName() != null) {
      builder.setName(dto.getName());
    }
    return builder.build();
  }

  private static LinkEventDefinitionDTO toDto(LinkEventDefinitionMessage message) {
    return new LinkEventDefinitionDTO(emptyToNull(message.getId()), emptyToNull(message.getName()));
  }

  private static TerminateEventDefinitionMessage toProto(TerminateEventDefinitionDTO dto) {
    TerminateEventDefinitionMessage.Builder builder = TerminateEventDefinitionMessage.newBuilder();
    if (dto != null) {
      setBaseElementFields(builder::setId, builder::setParentId, dto);
    }
    return builder.build();
  }

  private static TerminateEventDefinitionDTO toDto(TerminateEventDefinitionMessage message) {
    return new TerminateEventDefinitionDTO(emptyToNull(message.getId()));
  }

  private static EscalationEventDefinitionMessage toProto(EscalationEventDefinitionDTO dto) {
    EscalationEventDefinitionMessage.Builder builder =
        EscalationEventDefinitionMessage.newBuilder();
    if (dto == null) {
      return builder.build();
    }
    setBaseElementFields(builder::setId, builder::setParentId, dto);
    if (dto.getEscalationRef() != null) {
      builder.setEscalationRef(dto.getEscalationRef());
    }
    return builder.build();
  }

  private static EscalationEventDefinitionDTO toDto(EscalationEventDefinitionMessage message) {
    return new EscalationEventDefinitionDTO(
        emptyToNull(message.getId()), emptyToNull(message.getEscalationRef()));
  }

  private static TimerEventDefinitionMessage toProto(TimerEventDefinitionDTO dto) {
    TimerEventDefinitionMessage.Builder builder = TimerEventDefinitionMessage.newBuilder();
    if (dto == null) {
      return builder.build();
    }
    setBaseElementFields(builder::setId, builder::setParentId, dto);
    if (dto.getTimeDate() != null) {
      builder.setTimeDate(dto.getTimeDate());
    }
    if (dto.getTimeDuration() != null) {
      builder.setTimeDuration(dto.getTimeDuration());
    }
    if (dto.getTimeCycle() != null) {
      builder.setTimeCycle(dto.getTimeCycle());
    }
    return builder.build();
  }

  private static TimerEventDefinitionDTO toDto(TimerEventDefinitionMessage message) {
    return new TimerEventDefinitionDTO(
        emptyToNull(message.getId()),
        emptyToNull(message.getParentId()),
        message.getTimeDate(),
        message.getTimeDuration(),
        message.getTimeCycle());
  }

  private static ErrorEventDefinitionMessage toProto(ErrorEventDefinitionDTO dto) {
    ErrorEventDefinitionMessage.Builder builder = ErrorEventDefinitionMessage.newBuilder();
    if (dto == null) {
      return builder.build();
    }
    setBaseElementFields(builder::setId, builder::setParentId, dto);
    if (dto.getErrorRef() != null) {
      builder.setErrorRef(dto.getErrorRef());
    }
    return builder.build();
  }

  private static ErrorEventDefinitionDTO toDto(ErrorEventDefinitionMessage message) {
    return new ErrorEventDefinitionDTO(
        emptyToNull(message.getId()), emptyToNull(message.getErrorRef()));
  }

  private static MessageEventDefinitionMessage toProto(MessageEventDefinitionDTO dto) {
    MessageEventDefinitionMessage.Builder builder = MessageEventDefinitionMessage.newBuilder();
    if (dto == null) {
      return builder.build();
    }
    setBaseElementFields(builder::setId, builder::setParentId, dto);
    if (dto.getMessageRef() != null) {
      builder.setMessageRef(dto.getMessageRef());
    }
    return builder.build();
  }

  private static MessageEventDefinitionDTO toDto(MessageEventDefinitionMessage message) {
    return new MessageEventDefinitionDTO(
        emptyToNull(message.getId()), emptyToNull(message.getMessageRef()));
  }

  private static SignalEventDefinitionMessage toProto(SignalEventDefinitionDTO dto) {
    SignalEventDefinitionMessage.Builder builder = SignalEventDefinitionMessage.newBuilder();
    if (dto == null) {
      return builder.build();
    }
    setBaseElementFields(builder::setId, builder::setParentId, dto);
    if (dto.getSignalRef() != null) {
      builder.setSignalRef(dto.getSignalRef());
    }
    return builder.build();
  }

  private static SignalEventDefinitionDTO toDto(SignalEventDefinitionMessage message) {
    return new SignalEventDefinitionDTO(
        emptyToNull(message.getId()), emptyToNull(message.getSignalRef()));
  }

  private static CompensationEventDefinitionMessage toProto(CompensationEventDefinitionDTO dto) {
    CompensationEventDefinitionMessage.Builder builder =
        CompensationEventDefinitionMessage.newBuilder();
    if (dto == null) {
      return builder.build();
    }
    if (dto.getId() != null) {
      builder.setId(dto.getId());
    }
    if (dto.getActivityRef() != null) {
      builder.setActivityRef(dto.getActivityRef());
    }
    return builder.build();
  }

  private static CompensationEventDefinitionDTO toDto(CompensationEventDefinitionMessage message) {
    return new CompensationEventDefinitionDTO(
        emptyToNull(message.getId()), emptyToNull(message.getActivityRef()));
  }

  private static DefinitionsKeyMessage toProto(DefinitionsKey key) {
    DefinitionsKeyMessage.Builder builder = DefinitionsKeyMessage.newBuilder();
    if (key != null) {
      if (key.getProcessDefinitionId() != null) {
        builder.setProcessDefinitionId(key.getProcessDefinitionId());
      }
      if (key.getHash() != null) {
        builder.setHash(key.getHash());
      }
    }
    return builder.build();
  }

  private static DefinitionsKey toDto(DefinitionsKeyMessage message) {
    return new DefinitionsKey(
        emptyToNull(message.getProcessDefinitionId()), emptyToNull(message.getHash()));
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

  private static MessageMessage toProto(MessageDTO dto) {
    MessageMessage.Builder builder = MessageMessage.newBuilder();
    if (dto != null) {
      if (dto.getId() != null) {
        builder.setId(dto.getId());
      }
      if (dto.getName() != null) {
        builder.setName(dto.getName());
      }
      if (dto.getCorrelationKey() != null) {
        builder.setCorrelationKey(dto.getCorrelationKey());
      }
    }
    return builder.build();
  }

  private static MessageDTO toDto(MessageMessage message) {
    return new MessageDTO(
        emptyToNull(message.getId()),
        emptyToNull(message.getName()),
        emptyToNull(message.getCorrelationKey()));
  }

  private static EscalationDefinitionMessage toProto(EscalationDTO dto) {
    EscalationDefinitionMessage.Builder builder = EscalationDefinitionMessage.newBuilder();
    if (dto != null) {
      if (dto.getId() != null) {
        builder.setId(dto.getId());
      }
      if (dto.getName() != null) {
        builder.setName(dto.getName());
      }
      if (dto.getCode() != null) {
        builder.setCode(dto.getCode());
      }
    }
    return builder.build();
  }

  private static EscalationDTO toDto(EscalationDefinitionMessage message) {
    return new EscalationDTO(
        emptyToNull(message.getId()),
        emptyToNull(message.getName()),
        emptyToNull(message.getCode()));
  }

  private static ErrorDefinitionMessage toProto(ErrorDTO dto) {
    ErrorDefinitionMessage.Builder builder = ErrorDefinitionMessage.newBuilder();
    if (dto != null) {
      if (dto.getId() != null) {
        builder.setId(dto.getId());
      }
      if (dto.getName() != null) {
        builder.setName(dto.getName());
      }
      if (dto.getCode() != null) {
        builder.setCode(dto.getCode());
      }
    }
    return builder.build();
  }

  private static ErrorDTO toDto(ErrorDefinitionMessage message) {
    return new ErrorDTO(
        emptyToNull(message.getId()),
        emptyToNull(message.getName()),
        emptyToNull(message.getCode()));
  }

  private static SigMessage toProto(SigDTO dto) {
    SigMessage.Builder builder = SigMessage.newBuilder();
    if (dto != null) {
      if (dto.getId() != null) {
        builder.setId(dto.getId());
      }
      if (dto.getName() != null) {
        builder.setName(dto.getName());
      }
    }
    return builder.build();
  }

  private static SigDTO toDto(SigMessage message) {
    return new SigDTO(emptyToNull(message.getId()), emptyToNull(message.getName()));
  }

  private static InputOutputMappingMessage toProto(InputOutputMappingDTO dto) {
    InputOutputMappingMessage.Builder builder = InputOutputMappingMessage.newBuilder();
    if (dto == null) {
      return builder.build();
    }
    addIoMappings(builder::addInputMappings, dto.getInputMappings());
    addIoMappings(builder::addOutputMappings, dto.getOutputMappings());
    return builder.build();
  }

  private static InputOutputMappingDTO toDto(InputOutputMappingMessage message) {
    return new InputOutputMappingDTO(
        toIoVariableMappingSet(message.getInputMappingsList()),
        toIoVariableMappingSet(message.getOutputMappingsList()));
  }

  private static LoopCharacteristicsMessage toProto(LoopCharacteristicsDTO dto) {
    LoopCharacteristicsMessage.Builder builder = LoopCharacteristicsMessage.newBuilder();
    if (dto == null) {
      return builder.build();
    }
    builder.setSequential(dto.isSequential());
    if (dto.getInputCollection() != null) {
      builder.setInputCollection(dto.getInputCollection());
    }
    if (dto.getInputElement() != null) {
      builder.setInputElement(dto.getInputElement());
    }
    if (dto.getOutputCollection() != null) {
      builder.setOutputCollection(dto.getOutputCollection());
    }
    if (dto.getOutputElement() != null) {
      builder.setOutputElement(dto.getOutputElement());
    }
    return builder.build();
  }

  private static LoopCharacteristicsDTO toDto(LoopCharacteristicsMessage message) {
    return new LoopCharacteristicsDTO(
        message.getSequential(),
        message.getInputCollection(),
        message.getInputElement(),
        message.getOutputCollection(),
        message.getOutputElement());
  }

  private static FlowConditionMessage toProto(FlowConditionDTO dto) {
    FlowConditionMessage.Builder builder = FlowConditionMessage.newBuilder();
    if (dto != null && dto.getExpression() != null) {
      builder.setExpression(dto.getExpression());
    }
    return builder.build();
  }

  private static FlowConditionDTO toDto(FlowConditionMessage message) {
    return new FlowConditionDTO(message.getExpression());
  }

  private static IoVariableMappingMessage toProto(IoVariableMappingDTO dto) {
    IoVariableMappingMessage.Builder builder = IoVariableMappingMessage.newBuilder();
    if (dto != null) {
      if (dto.getSource() != null) {
        builder.setSource(dto.getSource());
      }
      if (dto.getTarget() != null) {
        builder.setTarget(dto.getTarget());
      }
    }
    return builder.build();
  }

  private static IoVariableMappingDTO toDto(IoVariableMappingMessage message) {
    return new IoVariableMappingDTO(
        emptyToNull(message.getSource()), emptyToNull(message.getTarget()));
  }

  private static AssignmentDefinitionMessage toProto(AssignmentDefinitionDTO dto) {
    AssignmentDefinitionMessage.Builder builder = AssignmentDefinitionMessage.newBuilder();
    if (dto != null) {
      if (dto.getAssignee() != null) {
        builder.setAssignee(dto.getAssignee());
      }
      if (dto.getCandidateGroups() != null) {
        builder.setCandidateGroups(dto.getCandidateGroups());
      }
      if (dto.getCandidateUsers() != null) {
        builder.setCandidateUsers(dto.getCandidateUsers());
      }
    }
    return builder.build();
  }

  private static AssignmentDefinitionDTO toDto(AssignmentDefinitionMessage message) {
    return new AssignmentDefinitionDTO(
        emptyToNull(message.getAssignee()),
        emptyToNull(message.getCandidateGroups()),
        emptyToNull(message.getCandidateUsers()));
  }

  private static TaskScheduleMessage toProto(TaskScheduleDTO dto) {
    TaskScheduleMessage.Builder builder = TaskScheduleMessage.newBuilder();
    if (dto != null) {
      if (dto.getDueDate() != null) {
        builder.setDueDate(dto.getDueDate());
      }
      if (dto.getFollowUpDate() != null) {
        builder.setFollowUpDate(dto.getFollowUpDate());
      }
    }
    return builder.build();
  }

  private static TaskScheduleDTO toDto(TaskScheduleMessage message) {
    return new TaskScheduleDTO(
        emptyToNull(message.getDueDate()), emptyToNull(message.getFollowUpDate()));
  }

  private static PriorityDefinitionMessage toProto(PriorityDefinitionDTO dto) {
    PriorityDefinitionMessage.Builder builder = PriorityDefinitionMessage.newBuilder();
    if (dto != null && dto.getPriority() != null) {
      builder.setPriority(dto.getPriority());
    }
    return builder.build();
  }

  private static PriorityDefinitionDTO toDto(PriorityDefinitionMessage message) {
    return new PriorityDefinitionDTO(emptyToNull(message.getPriority()));
  }

  private static io.taktx.proto.ProcessDefinitionStateEnum toProto(
      ProcessDefinitionStateEnum state) {
    if (state == null) {
      return io.taktx.proto.ProcessDefinitionStateEnum.PROCESS_DEFINITION_STATE_ACTIVE;
    }
    return switch (state) {
      case ACTIVE -> io.taktx.proto.ProcessDefinitionStateEnum.PROCESS_DEFINITION_STATE_ACTIVE;
      case INACTIVE -> io.taktx.proto.ProcessDefinitionStateEnum.PROCESS_DEFINITION_STATE_INACTIVE;
    };
  }

  private static ProcessDefinitionStateEnum toDto(io.taktx.proto.ProcessDefinitionStateEnum state) {
    return switch (state) {
      case PROCESS_DEFINITION_STATE_INACTIVE -> ProcessDefinitionStateEnum.INACTIVE;
      case PROCESS_DEFINITION_STATE_UNSPECIFIED, PROCESS_DEFINITION_STATE_ACTIVE, UNRECOGNIZED ->
          ProcessDefinitionStateEnum.ACTIVE;
    };
  }

  private static io.taktx.proto.UserTaskTypeEnum toProto(UserTaskTypeEnum type) {
    if (type == null) {
      return io.taktx.proto.UserTaskTypeEnum.USER_TASK_TYPE_ZEEBE;
    }
    return switch (type) {
      case ZEEBE -> io.taktx.proto.UserTaskTypeEnum.USER_TASK_TYPE_ZEEBE;
      case JOBWORKER -> io.taktx.proto.UserTaskTypeEnum.USER_TASK_TYPE_JOBWORKER;
    };
  }

  private static UserTaskTypeEnum toDto(io.taktx.proto.UserTaskTypeEnum type) {
    return switch (type) {
      case USER_TASK_TYPE_JOBWORKER -> UserTaskTypeEnum.JOBWORKER;
      case USER_TASK_TYPE_ZEEBE, UNRECOGNIZED -> UserTaskTypeEnum.ZEEBE;
    };
  }

  private static io.taktx.proto.ScriptType toProto(ScriptType type) {
    if (type == null) {
      return io.taktx.proto.ScriptType.UNKNOWN;
    }
    return switch (type) {
      case FEEL -> io.taktx.proto.ScriptType.FEEL;
      case JOBWORKER -> io.taktx.proto.ScriptType.JOBWORKER;
      case JAVASCRIPT -> io.taktx.proto.ScriptType.JAVASCRIPT;
      case GROOVY -> io.taktx.proto.ScriptType.GROOVY;
      case PYTHON -> io.taktx.proto.ScriptType.PYTHON;
      case RUBY -> io.taktx.proto.ScriptType.RUBY;
      case KOTLIN -> io.taktx.proto.ScriptType.KOTLIN;
      case BEANSHELL -> io.taktx.proto.ScriptType.BEANSHELL;
      case LUA -> io.taktx.proto.ScriptType.LUA;
      case PERL -> io.taktx.proto.ScriptType.PERL;
      case PHP -> io.taktx.proto.ScriptType.PHP;
      case SHELL -> io.taktx.proto.ScriptType.SHELL;
      case JUEL -> io.taktx.proto.ScriptType.JUEL;
      case UNKNOWN -> io.taktx.proto.ScriptType.UNKNOWN;
    };
  }

  private static ScriptType toDto(io.taktx.proto.ScriptType type) {
    return switch (type) {
      case FEEL -> ScriptType.FEEL;
      case JOBWORKER -> ScriptType.JOBWORKER;
      case JAVASCRIPT -> ScriptType.JAVASCRIPT;
      case GROOVY -> ScriptType.GROOVY;
      case PYTHON -> ScriptType.PYTHON;
      case RUBY -> ScriptType.RUBY;
      case KOTLIN -> ScriptType.KOTLIN;
      case BEANSHELL -> ScriptType.BEANSHELL;
      case LUA -> ScriptType.LUA;
      case PERL -> ScriptType.PERL;
      case PHP -> ScriptType.PHP;
      case SHELL -> ScriptType.SHELL;
      case JUEL -> ScriptType.JUEL;
      case UNKNOWN, UNRECOGNIZED -> ScriptType.UNKNOWN;
    };
  }

  private static Set<EventDefinitionDTO> toEventDefinitionSet(List<BaseElementEnvelope> envelopes) {
    LinkedHashSet<EventDefinitionDTO> values = new LinkedHashSet<>();
    for (BaseElementEnvelope envelope : envelopes) {
      BaseElementDTO dto = toDto(envelope);
      if (!(dto instanceof EventDefinitionDTO eventDefinition)) {
        throw new IllegalArgumentException(
            "Expected event definition envelope but received " + dto);
      }
      values.add(eventDefinition);
    }
    return values;
  }

  private static Set<IoVariableMappingDTO> toIoVariableMappingSet(
      List<IoVariableMappingMessage> messages) {
    LinkedHashSet<IoVariableMappingDTO> values = new LinkedHashSet<>();
    for (IoVariableMappingMessage message : messages) {
      values.add(toDto(message));
    }
    return values;
  }

  private static void addEventDefinitions(
      java.util.function.Consumer<BaseElementEnvelope> consumer, Set<EventDefinitionDTO> values) {
    if (values == null) {
      return;
    }
    values.stream().map(DefinitionsProtoMapper::toProto).forEach(consumer);
  }

  private static void addIoMappings(
      java.util.function.Consumer<IoVariableMappingMessage> consumer,
      Set<IoVariableMappingDTO> values) {
    if (values == null) {
      return;
    }
    values.stream().map(DefinitionsProtoMapper::toProto).forEach(consumer);
  }

  private static <V, P> void putMap(
      Map<String, V> values,
      java.util.function.BiConsumer<String, P> putter,
      java.util.function.Function<V, P> mapper) {
    if (values == null) {
      return;
    }
    values.forEach((key, value) -> putter.accept(key, mapper.apply(value)));
  }

  private static <P, V> Map<String, V> toDtoMap(
      Map<String, P> values, java.util.function.Function<P, V> mapper) {
    Map<String, V> mapped = new LinkedHashMap<>();
    values.forEach((key, value) -> mapped.put(key, mapper.apply(value)));
    return mapped;
  }

  private static Set<String> stringSet(List<String> values) {
    return new LinkedHashSet<>(values);
  }

  private static void setBaseElementFields(
      java.util.function.Consumer<String> idSetter,
      java.util.function.Consumer<String> parentIdSetter,
      BaseElementDTO dto) {
    if (dto.getId() != null) {
      idSetter.accept(dto.getId());
    }
    if (dto.getParentId() != null) {
      parentIdSetter.accept(dto.getParentId());
    }
  }

  private static void setFlowNodeFields(
      java.util.function.Consumer<String> idSetter,
      java.util.function.Consumer<String> parentIdSetter,
      java.util.function.Consumer<String> nameSetter,
      java.util.function.Consumer<Iterable<String>> incomingSetter,
      java.util.function.Consumer<Iterable<String>> outgoingSetter,
      FlowNodeDTO dto) {
    setBaseElementFields(idSetter, parentIdSetter, dto);
    if (dto.getName() != null) {
      nameSetter.accept(dto.getName());
    }
    if (dto.getIncoming() != null) {
      incomingSetter.accept(dto.getIncoming());
    }
    if (dto.getOutgoing() != null) {
      outgoingSetter.accept(dto.getOutgoing());
    }
  }

  private static void setActivityFields(
      java.util.function.Consumer<String> idSetter,
      java.util.function.Consumer<String> parentIdSetter,
      java.util.function.Consumer<String> nameSetter,
      java.util.function.Consumer<Iterable<String>> incomingSetter,
      java.util.function.Consumer<Iterable<String>> outgoingSetter,
      java.util.function.Consumer<LoopCharacteristicsMessage> loopSetter,
      java.util.function.Consumer<InputOutputMappingMessage> ioSetter,
      ActivityDTO dto) {
    setFlowNodeFields(idSetter, parentIdSetter, nameSetter, incomingSetter, outgoingSetter, dto);
    if (dto.getLoopCharacteristics() != null) {
      loopSetter.accept(toProto(dto.getLoopCharacteristics()));
    }
    if (dto.getIoMapping() != null) {
      ioSetter.accept(toProto(dto.getIoMapping()));
    }
  }

  private static void setGatewayFields(
      java.util.function.Consumer<String> idSetter,
      java.util.function.Consumer<String> parentIdSetter,
      java.util.function.Consumer<String> nameSetter,
      java.util.function.Consumer<Iterable<String>> incomingSetter,
      java.util.function.Consumer<Iterable<String>> outgoingSetter,
      java.util.function.Consumer<String> defaultFlowSetter,
      GatewayDTO dto) {
    setFlowNodeFields(idSetter, parentIdSetter, nameSetter, incomingSetter, outgoingSetter, dto);
    if (dto.getDefaultFlow() != null) {
      defaultFlowSetter.accept(dto.getDefaultFlow());
    }
  }

  private static void setExternalTaskFields(
      java.util.function.Consumer<String> idSetter,
      java.util.function.Consumer<String> parentIdSetter,
      java.util.function.Consumer<String> nameSetter,
      java.util.function.Consumer<Iterable<String>> incomingSetter,
      java.util.function.Consumer<Iterable<String>> outgoingSetter,
      java.util.function.Consumer<LoopCharacteristicsMessage> loopSetter,
      java.util.function.Consumer<InputOutputMappingMessage> ioSetter,
      java.util.function.Consumer<String> workerDefinitionSetter,
      java.util.function.Consumer<String> retriesSetter,
      java.util.function.Consumer<String> implementationSetter,
      java.util.function.Consumer<Map<String, String>> headersSetter,
      io.taktx.dto.ExternalTaskDTO dto) {
    setActivityFields(
        idSetter,
        parentIdSetter,
        nameSetter,
        incomingSetter,
        outgoingSetter,
        loopSetter,
        ioSetter,
        dto);
    if (dto.getWorkerDefinition() != null) {
      workerDefinitionSetter.accept(dto.getWorkerDefinition());
    }
    if (dto.getRetries() != null) {
      retriesSetter.accept(dto.getRetries());
    }
    if (dto.getImplementation() != null) {
      implementationSetter.accept(dto.getImplementation());
    }
    if (dto.getHeaders() != null) {
      headersSetter.accept(dto.getHeaders());
    }
  }

  private static String emptyToNull(String value) {
    return value == null || value.isEmpty() ? null : value;
  }
}
