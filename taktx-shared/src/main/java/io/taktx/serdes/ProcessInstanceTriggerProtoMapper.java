/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.serdes;

import io.taktx.dto.AbortTriggerDTO;
import io.taktx.dto.CommandAuthMethod;
import io.taktx.dto.CommandTrustMetadataDTO;
import io.taktx.dto.CommandTrustVerificationResult;
import io.taktx.dto.ContinueFlowElementTriggerDTO;
import io.taktx.dto.ErrorEventSignalDTO;
import io.taktx.dto.EscalationEventSignalDTO;
import io.taktx.dto.EventSignalDTO;
import io.taktx.dto.EventSignalTriggerDTO;
import io.taktx.dto.ExternalTaskResponseResultDTO;
import io.taktx.dto.ExternalTaskResponseTriggerDTO;
import io.taktx.dto.ExternalTaskResponseType;
import io.taktx.dto.IoVariableMappingDTO;
import io.taktx.dto.MessageEventSignalDTO;
import io.taktx.dto.ProcessDefinitionKey;
import io.taktx.dto.ProcessInstanceTriggerDTO;
import io.taktx.dto.SetVariableTriggerDTO;
import io.taktx.dto.SignalEventSignalDTO;
import io.taktx.dto.StartCommandDTO;
import io.taktx.dto.StartFlowElementTriggerDTO;
import io.taktx.dto.TimerEventSignalDTO;
import io.taktx.dto.UserTaskResponseResultDTO;
import io.taktx.dto.UserTaskResponseTriggerDTO;
import io.taktx.dto.UserTaskResponseType;
import io.taktx.dto.VariablesDTO;
import io.taktx.proto.AbortTriggerMessage;
import io.taktx.proto.CommandTrustMetadataMessage;
import io.taktx.proto.ContinueFlowElementTriggerMessage;
import io.taktx.proto.ErrorEventSignalMessage;
import io.taktx.proto.EscalationEventSignalMessage;
import io.taktx.proto.EventSignalEnvelope;
import io.taktx.proto.EventSignalTriggerMessage;
import io.taktx.proto.ExternalTaskResponseResultMessage;
import io.taktx.proto.ExternalTaskResponseTriggerMessage;
import io.taktx.proto.IoVariableMappingMessage;
import io.taktx.proto.MessageEventSignalMessage;
import io.taktx.proto.ProcessDefinitionKeyMessage;
import io.taktx.proto.ProcessInstanceTriggerEnvelope;
import io.taktx.proto.SetVariableTriggerMessage;
import io.taktx.proto.SignalEventSignalMessage;
import io.taktx.proto.StartCommandMessage;
import io.taktx.proto.StartFlowElementTriggerMessage;
import io.taktx.proto.TimerEventSignalMessage;
import io.taktx.proto.UserTaskResponseResultMessage;
import io.taktx.proto.UserTaskResponseTriggerMessage;
import io.taktx.proto.Uuid;
import io.taktx.proto.VarMap;
import io.taktx.variables.VariableValueDtoMapper;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Shared DTO ↔ protobuf mapper for process-instance trigger records. */
public final class ProcessInstanceTriggerProtoMapper {

  private ProcessInstanceTriggerProtoMapper() {}

  public static ProcessInstanceTriggerEnvelope toProto(ProcessInstanceTriggerDTO dto) {
    ProcessInstanceTriggerEnvelope.Builder envelope = ProcessInstanceTriggerEnvelope.newBuilder();
    if (dto == null) {
      return envelope.build();
    }
    if (dto instanceof StartCommandDTO startCommand) {
      envelope.setStart(toProto(startCommand));
    } else if (dto instanceof ExternalTaskResponseTriggerDTO externalTaskResponse) {
      envelope.setExtTaskResponse(toProto(externalTaskResponse));
    } else if (dto instanceof UserTaskResponseTriggerDTO userTaskResponse) {
      envelope.setUserTaskResponse(toProto(userTaskResponse));
    } else if (dto instanceof ContinueFlowElementTriggerDTO continueTrigger) {
      envelope.setContinueFlow(toProto(continueTrigger));
    } else if (dto instanceof StartFlowElementTriggerDTO startFlowElement) {
      envelope.setStartFlowElement(toProto(startFlowElement));
    } else if (dto instanceof SetVariableTriggerDTO setVariable) {
      envelope.setSetVariable(toProto(setVariable));
    } else if (dto instanceof AbortTriggerDTO abortTrigger) {
      envelope.setAbort(toProto(abortTrigger));
    } else if (dto instanceof EventSignalTriggerDTO eventSignalTrigger) {
      envelope.setEventSignal(toProto(eventSignalTrigger));
    } else {
      throw new IllegalArgumentException(
          "Unsupported process-instance trigger type: " + dto.getClass().getName());
    }
    return envelope.build();
  }

  public static ProcessInstanceTriggerDTO toDto(ProcessInstanceTriggerEnvelope envelope) {
    if (envelope == null) {
      return null;
    }
    return switch (envelope.getTriggerCase()) {
      case START -> toDto(envelope.getStart());
      case CONTINUE_FLOW -> toDto(envelope.getContinueFlow());
      case EXT_TASK_RESPONSE -> toDto(envelope.getExtTaskResponse());
      case START_FLOW_ELEMENT -> toDto(envelope.getStartFlowElement());
      case SET_VARIABLE -> toDto(envelope.getSetVariable());
      case ABORT -> toDto(envelope.getAbort());
      case USER_TASK_RESPONSE -> toDto(envelope.getUserTaskResponse());
      case EVENT_SIGNAL -> toDto(envelope.getEventSignal());
      case EXTERNAL_TASK, TRIGGER_NOT_SET -> null;
    };
  }

  private static StartCommandMessage toProto(StartCommandDTO dto) {
    StartCommandMessage.Builder builder = StartCommandMessage.newBuilder();
    setBaseTriggerFields(builder, dto);
    if (dto.getParentProcessInstanceId() != null) {
      builder.setParentProcessInstanceId(toProto(dto.getParentProcessInstanceId()));
    }
    if (dto.getElementId() != null) {
      builder.setElementId(dto.getElementId());
    }
    if (dto.getParentElementInstancePath() != null) {
      builder.addAllParentElementInstancePath(dto.getParentElementInstancePath());
    }
    if (dto.getProcessDefinitionKey() != null) {
      builder.setProcessDefinitionKey(toProto(dto.getProcessDefinitionKey()));
    }
    if (dto.getVariables() != null) {
      builder.setVariables(toProto(dto.getVariables()));
    }
    builder.setPropagateAllToParent(dto.isPropagateAllToParent());
    if (dto.getOutputMappings() != null) {
      dto.getOutputMappings().stream()
          .map(ProcessInstanceTriggerProtoMapper::toProto)
          .forEach(builder::addOutputMappings);
    }
    if (dto.getBusinessKey() != null) {
      builder.setBusinessKey(dto.getBusinessKey());
    }
    if (dto.getTags() != null) {
      builder.addAllTags(dto.getTags());
    }
    return builder.build();
  }

  private static StartCommandDTO toDto(StartCommandMessage message) {
    StartCommandDTO dto =
        new StartCommandDTO(
            message.hasProcessInstanceId() ? toDto(message.getProcessInstanceId()) : null,
            message.hasParentProcessInstanceId()
                ? toDto(message.getParentProcessInstanceId())
                : null,
            emptyToNull(message.getElementId()),
            message.getParentElementInstancePathCount() == 0
                ? null
                : new ArrayList<>(message.getParentElementInstancePathList()),
            message.hasProcessDefinitionKey() ? toDto(message.getProcessDefinitionKey()) : null,
            message.hasVariables() ? toVariablesDto(message.getVariables()) : null,
            message.getPropagateAllToParent(),
            toIoVariableMappingSet(message.getOutputMappingsList()),
            emptyToNull(message.getBusinessKey()),
            message.getTagsCount() == 0 ? Set.of() : new LinkedHashSet<>(message.getTagsList()));
    applyTrustMetadata(
        dto,
        message.hasCurrentTrustMetadata() ? toDto(message.getCurrentTrustMetadata()) : null,
        message.hasOriginTrustMetadata() ? toDto(message.getOriginTrustMetadata()) : null);
    return dto;
  }

  private static ContinueFlowElementTriggerMessage toProto(ContinueFlowElementTriggerDTO dto) {
    ContinueFlowElementTriggerMessage.Builder builder =
        ContinueFlowElementTriggerMessage.newBuilder();
    setBaseTriggerFields(builder, dto);
    if (dto.getInputFlowId() != null) {
      builder.setInputFlowId(dto.getInputFlowId());
    }
    if (dto.getVariables() != null) {
      builder.setVariables(toProto(dto.getVariables()));
    }
    if (dto.getElementInstanceIdPath() != null) {
      builder.addAllElementInstanceIdPath(dto.getElementInstanceIdPath());
    }
    return builder.build();
  }

  private static ContinueFlowElementTriggerDTO toDto(ContinueFlowElementTriggerMessage message) {
    ContinueFlowElementTriggerDTO dto =
        new ContinueFlowElementTriggerDTO(
            message.hasProcessInstanceId() ? toDto(message.getProcessInstanceId()) : null,
            new ArrayList<>(message.getElementInstanceIdPathList()),
            emptyToNull(message.getInputFlowId()),
            message.hasVariables() ? toVariablesDto(message.getVariables()) : null);
    applyTrustMetadata(
        dto,
        message.hasCurrentTrustMetadata() ? toDto(message.getCurrentTrustMetadata()) : null,
        message.hasOriginTrustMetadata() ? toDto(message.getOriginTrustMetadata()) : null);
    return dto;
  }

  private static StartFlowElementTriggerMessage toProto(StartFlowElementTriggerDTO dto) {
    StartFlowElementTriggerMessage.Builder builder = StartFlowElementTriggerMessage.newBuilder();
    setBaseTriggerFields(builder, dto);
    if (dto.getParentElementInstanceIdPath() != null) {
      builder.addAllParentElementInstanceIdPath(dto.getParentElementInstanceIdPath());
    }
    if (dto.getElementId() != null) {
      builder.setElementId(dto.getElementId());
    }
    if (dto.getVariables() != null) {
      builder.setVariables(toProto(dto.getVariables()));
    }
    return builder.build();
  }

  private static StartFlowElementTriggerDTO toDto(StartFlowElementTriggerMessage message) {
    StartFlowElementTriggerDTO dto =
        new StartFlowElementTriggerDTO(
            message.hasProcessInstanceId() ? toDto(message.getProcessInstanceId()) : null,
            new ArrayList<>(message.getParentElementInstanceIdPathList()),
            emptyToNull(message.getElementId()),
            message.hasVariables() ? toVariablesDto(message.getVariables()) : null);
    applyTrustMetadata(
        dto,
        message.hasCurrentTrustMetadata() ? toDto(message.getCurrentTrustMetadata()) : null,
        message.hasOriginTrustMetadata() ? toDto(message.getOriginTrustMetadata()) : null);
    return dto;
  }

  private static SetVariableTriggerMessage toProto(SetVariableTriggerDTO dto) {
    SetVariableTriggerMessage.Builder builder = SetVariableTriggerMessage.newBuilder();
    setBaseTriggerFields(builder, dto);
    if (dto.getParentElementInstanceIdPath() != null) {
      builder.addAllParentElementInstanceIdPath(dto.getParentElementInstanceIdPath());
    }
    if (dto.getVariables() != null) {
      builder.setVariables(toProto(dto.getVariables()));
    }
    return builder.build();
  }

  private static SetVariableTriggerDTO toDto(SetVariableTriggerMessage message) {
    SetVariableTriggerDTO dto =
        new SetVariableTriggerDTO(
            message.hasProcessInstanceId() ? toDto(message.getProcessInstanceId()) : null,
            new ArrayList<>(message.getParentElementInstanceIdPathList()),
            message.hasVariables() ? toVariablesDto(message.getVariables()) : null);
    applyTrustMetadata(
        dto,
        message.hasCurrentTrustMetadata() ? toDto(message.getCurrentTrustMetadata()) : null,
        message.hasOriginTrustMetadata() ? toDto(message.getOriginTrustMetadata()) : null);
    return dto;
  }

  private static AbortTriggerMessage toProto(AbortTriggerDTO dto) {
    AbortTriggerMessage.Builder builder = AbortTriggerMessage.newBuilder();
    setBaseTriggerFields(builder, dto);
    if (dto.getElementInstanceIdPath() != null) {
      builder.addAllElementInstanceIdPath(dto.getElementInstanceIdPath());
    }
    return builder.build();
  }

  private static AbortTriggerDTO toDto(AbortTriggerMessage message) {
    AbortTriggerDTO dto =
        new AbortTriggerDTO(
            message.hasProcessInstanceId() ? toDto(message.getProcessInstanceId()) : null,
            new ArrayList<>(message.getElementInstanceIdPathList()));
    applyTrustMetadata(
        dto,
        message.hasCurrentTrustMetadata() ? toDto(message.getCurrentTrustMetadata()) : null,
        message.hasOriginTrustMetadata() ? toDto(message.getOriginTrustMetadata()) : null);
    return dto;
  }

  private static EventSignalTriggerMessage toProto(EventSignalTriggerDTO dto) {
    EventSignalTriggerMessage.Builder builder = EventSignalTriggerMessage.newBuilder();
    setBaseTriggerFields(builder, dto);
    if (dto.getEventSignal() != null) {
      builder.setEventSignal(toProto(dto.getEventSignal()));
    }
    return builder.build();
  }

  private static EventSignalTriggerDTO toDto(EventSignalTriggerMessage message) {
    EventSignalTriggerDTO dto =
        new EventSignalTriggerDTO(
            message.hasProcessInstanceId() ? toDto(message.getProcessInstanceId()) : null,
            message.hasEventSignal() ? toDto(message.getEventSignal()) : null);
    applyTrustMetadata(
        dto,
        message.hasCurrentTrustMetadata() ? toDto(message.getCurrentTrustMetadata()) : null,
        message.hasOriginTrustMetadata() ? toDto(message.getOriginTrustMetadata()) : null);
    return dto;
  }

  private static ExternalTaskResponseTriggerMessage toProto(ExternalTaskResponseTriggerDTO dto) {
    ExternalTaskResponseTriggerMessage.Builder builder =
        ExternalTaskResponseTriggerMessage.newBuilder();
    setBaseTriggerFields(builder, dto);
    if (dto.getInputFlowId() != null) {
      builder.setInputFlowId(dto.getInputFlowId());
    }
    if (dto.getVariables() != null) {
      builder.setVariables(toProto(dto.getVariables()));
    }
    if (dto.getElementInstanceIdPath() != null) {
      builder.addAllElementInstanceIdPath(dto.getElementInstanceIdPath());
    }
    if (dto.getMessageId() != null) {
      builder.setMessageId(dto.getMessageId());
    }
    if (dto.getExternalTaskResponseResult() != null) {
      builder.setExternalTaskResponseResult(toProto(dto.getExternalTaskResponseResult()));
    }
    return builder.build();
  }

  private static ExternalTaskResponseTriggerDTO toDto(ExternalTaskResponseTriggerMessage message) {
    ExternalTaskResponseTriggerDTO dto =
        new ExternalTaskResponseTriggerDTO(
            message.hasProcessInstanceId() ? toDto(message.getProcessInstanceId()) : null,
            new ArrayList<>(message.getElementInstanceIdPathList()),
            emptyToNull(message.getMessageId()),
            message.hasExternalTaskResponseResult()
                ? toDto(message.getExternalTaskResponseResult())
                : null,
            message.hasVariables() ? toVariablesDto(message.getVariables()) : null);
    applyTrustMetadata(
        dto,
        message.hasCurrentTrustMetadata() ? toDto(message.getCurrentTrustMetadata()) : null,
        message.hasOriginTrustMetadata() ? toDto(message.getOriginTrustMetadata()) : null);
    return dto;
  }

  private static UserTaskResponseTriggerMessage toProto(UserTaskResponseTriggerDTO dto) {
    UserTaskResponseTriggerMessage.Builder builder = UserTaskResponseTriggerMessage.newBuilder();
    setBaseTriggerFields(builder, dto);
    if (dto.getInputFlowId() != null) {
      builder.setInputFlowId(dto.getInputFlowId());
    }
    if (dto.getVariables() != null) {
      builder.setVariables(toProto(dto.getVariables()));
    }
    if (dto.getElementInstanceIdPath() != null) {
      builder.addAllElementInstanceIdPath(dto.getElementInstanceIdPath());
    }
    if (dto.getMessageId() != null) {
      builder.setMessageId(dto.getMessageId());
    }
    if (dto.getUserTaskResponseResult() != null) {
      builder.setUserTaskResponseResult(toProto(dto.getUserTaskResponseResult()));
    }
    return builder.build();
  }

  private static UserTaskResponseTriggerDTO toDto(UserTaskResponseTriggerMessage message) {
    UserTaskResponseTriggerDTO dto =
        new UserTaskResponseTriggerDTO(
            message.hasProcessInstanceId() ? toDto(message.getProcessInstanceId()) : null,
            new ArrayList<>(message.getElementInstanceIdPathList()),
            emptyToNull(message.getMessageId()),
            message.hasUserTaskResponseResult() ? toDto(message.getUserTaskResponseResult()) : null,
            message.hasVariables() ? toVariablesDto(message.getVariables()) : null);
    applyTrustMetadata(
        dto,
        message.hasCurrentTrustMetadata() ? toDto(message.getCurrentTrustMetadata()) : null,
        message.hasOriginTrustMetadata() ? toDto(message.getOriginTrustMetadata()) : null);
    return dto;
  }

  private static ExternalTaskResponseResultMessage toProto(ExternalTaskResponseResultDTO dto) {
    ExternalTaskResponseResultMessage.Builder builder =
        ExternalTaskResponseResultMessage.newBuilder();
    if (dto.getResponseType() != null) {
      builder.setResponseType(toProto(dto.getResponseType()));
    }
    if (dto.getCode() != null) {
      builder.setCode(dto.getCode());
    }
    if (dto.getMessage() != null) {
      builder.setMessage(dto.getMessage());
    }
    if (dto.getAllowRetry() != null) {
      builder.setAllowRetry(dto.getAllowRetry());
    }
    builder.setTimeout(dto.getTimeout());
    if (dto.getStacktrace() != null) {
      builder.addAllStacktrace(List.of(dto.getStacktrace()));
    }
    return builder.build();
  }

  private static ExternalTaskResponseResultDTO toDto(ExternalTaskResponseResultMessage message) {
    return new ExternalTaskResponseResultDTO(
        toDto(message.getResponseType()),
        message.hasAllowRetry() ? message.getAllowRetry() : null,
        emptyToNull(message.getCode()),
        emptyToNull(message.getMessage()),
        message.getTimeout(),
        message.getStacktraceCount() == 0
            ? null
            : message.getStacktraceList().toArray(String[]::new));
  }

  private static UserTaskResponseResultMessage toProto(UserTaskResponseResultDTO dto) {
    UserTaskResponseResultMessage.Builder builder = UserTaskResponseResultMessage.newBuilder();
    if (dto.getResponseType() != null) {
      builder.setResponseType(toProto(dto.getResponseType()));
    }
    if (dto.getCode() != null) {
      builder.setCode(dto.getCode());
    }
    if (dto.getMessage() != null) {
      builder.setMessage(dto.getMessage());
    }
    return builder.build();
  }

  private static UserTaskResponseResultDTO toDto(UserTaskResponseResultMessage message) {
    return new UserTaskResponseResultDTO(
        toDto(message.getResponseType()),
        emptyToNull(message.getCode()),
        emptyToNull(message.getMessage()));
  }

  private static EventSignalEnvelope toProto(EventSignalDTO dto) {
    EventSignalEnvelope.Builder builder = EventSignalEnvelope.newBuilder();
    switch (dto) {
      case MessageEventSignalDTO messageEvent -> {
        MessageEventSignalMessage.Builder signal = MessageEventSignalMessage.newBuilder();
        setBaseEventSignalFields(signal, messageEvent);
        if (messageEvent.getElementId() != null) {
          signal.setElementId(messageEvent.getElementId());
        }
        if (messageEvent.getName() != null) {
          signal.setName(messageEvent.getName());
        }
        builder.setMessageSignal(signal.build());
      }
      case ErrorEventSignalDTO error -> {
        ErrorEventSignalMessage.Builder signal = ErrorEventSignalMessage.newBuilder();
        setBaseEventSignalFields(signal, error);
        if (error.getCode() != null) {
          signal.setCode(error.getCode());
        }
        if (error.getMessage() != null) {
          signal.setMessage(error.getMessage());
        }
        builder.setErrorSignal(signal.build());
      }
      case EscalationEventSignalDTO escalation -> {
        EscalationEventSignalMessage.Builder signal = EscalationEventSignalMessage.newBuilder();
        setBaseEventSignalFields(signal, escalation);
        if (escalation.getCode() != null) {
          signal.setCode(escalation.getCode());
        }
        if (escalation.getMessage() != null) {
          signal.setMessage(escalation.getMessage());
        }
        builder.setEscalationSignal(signal.build());
      }
      case TimerEventSignalDTO timer -> {
        TimerEventSignalMessage.Builder signal = TimerEventSignalMessage.newBuilder();
        setBaseEventSignalFields(signal, timer);
        if (timer.getElementId() != null) {
          signal.setElementId(timer.getElementId());
        }
        builder.setTimerSignal(signal.build());
      }
      case SignalEventSignalDTO signalEvent -> {
        SignalEventSignalMessage.Builder signal = SignalEventSignalMessage.newBuilder();
        setBaseEventSignalFields(signal, signalEvent);
        if (signalEvent.getName() != null) {
          signal.setName(signalEvent.getName());
        }
        builder.setSignalSignal(signal.build());
      }
      default ->
          throw new IllegalArgumentException(
              "Unsupported event signal type: " + dto.getClass().getName());
    }
    return builder.build();
  }

  private static EventSignalDTO toDto(EventSignalEnvelope envelope) {
    if (envelope == null) {
      return null;
    }
    return switch (envelope.getSignalCase()) {
      case MESSAGE_SIGNAL -> toDto(envelope.getMessageSignal());
      case ERROR_SIGNAL -> toDto(envelope.getErrorSignal());
      case ESCALATION_SIGNAL -> toDto(envelope.getEscalationSignal());
      case TIMER_SIGNAL -> toDto(envelope.getTimerSignal());
      case SIGNAL_SIGNAL -> toDto(envelope.getSignalSignal());
      case SIGNAL_NOT_SET -> null;
    };
  }

  private static MessageEventSignalDTO toDto(MessageEventSignalMessage message) {
    MessageEventSignalDTO dto =
        new MessageEventSignalDTO(
            emptyToNull(message.getElementId()), emptyToNull(message.getName()));
    applyBaseEventSignal(
        dto,
        message.getElementInstanceIdPathList(),
        message.hasVariables() ? toVariablesDto(message.getVariables()) : null);
    return dto;
  }

  private static ErrorEventSignalDTO toDto(ErrorEventSignalMessage message) {
    ErrorEventSignalDTO dto =
        new ErrorEventSignalDTO(emptyToNull(message.getCode()), emptyToNull(message.getMessage()));
    applyBaseEventSignal(
        dto,
        message.getElementInstanceIdPathList(),
        message.hasVariables() ? toVariablesDto(message.getVariables()) : null);
    return dto;
  }

  private static EscalationEventSignalDTO toDto(EscalationEventSignalMessage message) {
    EscalationEventSignalDTO dto =
        new EscalationEventSignalDTO(
            emptyToNull(message.getCode()), emptyToNull(message.getMessage()));
    applyBaseEventSignal(
        dto,
        message.getElementInstanceIdPathList(),
        message.hasVariables() ? toVariablesDto(message.getVariables()) : null);
    return dto;
  }

  private static TimerEventSignalDTO toDto(TimerEventSignalMessage message) {
    TimerEventSignalDTO dto = new TimerEventSignalDTO(emptyToNull(message.getElementId()));
    applyBaseEventSignal(
        dto,
        message.getElementInstanceIdPathList(),
        message.hasVariables() ? toVariablesDto(message.getVariables()) : null);
    return dto;
  }

  private static SignalEventSignalDTO toDto(SignalEventSignalMessage message) {
    SignalEventSignalDTO dto = new SignalEventSignalDTO(emptyToNull(message.getName()));
    applyBaseEventSignal(
        dto,
        message.getElementInstanceIdPathList(),
        message.hasVariables() ? toVariablesDto(message.getVariables()) : null);
    return dto;
  }

  private static void setBaseTriggerFields(
      StartCommandMessage.Builder builder, ProcessInstanceTriggerDTO dto) {
    setCommonTriggerFields(builder, dto);
  }

  private static void setBaseTriggerFields(
      ContinueFlowElementTriggerMessage.Builder builder, ProcessInstanceTriggerDTO dto) {
    setCommonTriggerFields(builder, dto);
  }

  private static void setBaseTriggerFields(
      StartFlowElementTriggerMessage.Builder builder, ProcessInstanceTriggerDTO dto) {
    setCommonTriggerFields(builder, dto);
  }

  private static void setBaseTriggerFields(
      SetVariableTriggerMessage.Builder builder, ProcessInstanceTriggerDTO dto) {
    setCommonTriggerFields(builder, dto);
  }

  private static void setBaseTriggerFields(
      AbortTriggerMessage.Builder builder, ProcessInstanceTriggerDTO dto) {
    setCommonTriggerFields(builder, dto);
  }

  private static void setBaseTriggerFields(
      EventSignalTriggerMessage.Builder builder, ProcessInstanceTriggerDTO dto) {
    setCommonTriggerFields(builder, dto);
  }

  private static void setBaseTriggerFields(
      ExternalTaskResponseTriggerMessage.Builder builder, ProcessInstanceTriggerDTO dto) {
    setCommonTriggerFields(builder, dto);
  }

  private static void setBaseTriggerFields(
      UserTaskResponseTriggerMessage.Builder builder, ProcessInstanceTriggerDTO dto) {
    setCommonTriggerFields(builder, dto);
  }

  private static void setCommonTriggerFields(
      StartCommandMessage.Builder builder, ProcessInstanceTriggerDTO dto) {
    if (dto.getProcessInstanceId() != null) {
      builder.setProcessInstanceId(toProto(dto.getProcessInstanceId()));
    }
    if (dto.getCurrentTrustMetadata() != null) {
      builder.setCurrentTrustMetadata(toProto(dto.getCurrentTrustMetadata()));
    }
    if (dto.getOriginTrustMetadata() != null) {
      builder.setOriginTrustMetadata(toProto(dto.getOriginTrustMetadata()));
    }
  }

  private static void setCommonTriggerFields(
      ContinueFlowElementTriggerMessage.Builder builder, ProcessInstanceTriggerDTO dto) {
    if (dto.getProcessInstanceId() != null) {
      builder.setProcessInstanceId(toProto(dto.getProcessInstanceId()));
    }
    if (dto.getCurrentTrustMetadata() != null) {
      builder.setCurrentTrustMetadata(toProto(dto.getCurrentTrustMetadata()));
    }
    if (dto.getOriginTrustMetadata() != null) {
      builder.setOriginTrustMetadata(toProto(dto.getOriginTrustMetadata()));
    }
  }

  private static void setCommonTriggerFields(
      StartFlowElementTriggerMessage.Builder builder, ProcessInstanceTriggerDTO dto) {
    if (dto.getProcessInstanceId() != null) {
      builder.setProcessInstanceId(toProto(dto.getProcessInstanceId()));
    }
    if (dto.getCurrentTrustMetadata() != null) {
      builder.setCurrentTrustMetadata(toProto(dto.getCurrentTrustMetadata()));
    }
    if (dto.getOriginTrustMetadata() != null) {
      builder.setOriginTrustMetadata(toProto(dto.getOriginTrustMetadata()));
    }
  }

  private static void setCommonTriggerFields(
      SetVariableTriggerMessage.Builder builder, ProcessInstanceTriggerDTO dto) {
    if (dto.getProcessInstanceId() != null) {
      builder.setProcessInstanceId(toProto(dto.getProcessInstanceId()));
    }
    if (dto.getCurrentTrustMetadata() != null) {
      builder.setCurrentTrustMetadata(toProto(dto.getCurrentTrustMetadata()));
    }
    if (dto.getOriginTrustMetadata() != null) {
      builder.setOriginTrustMetadata(toProto(dto.getOriginTrustMetadata()));
    }
  }

  private static void setCommonTriggerFields(
      AbortTriggerMessage.Builder builder, ProcessInstanceTriggerDTO dto) {
    if (dto.getProcessInstanceId() != null) {
      builder.setProcessInstanceId(toProto(dto.getProcessInstanceId()));
    }
    if (dto.getCurrentTrustMetadata() != null) {
      builder.setCurrentTrustMetadata(toProto(dto.getCurrentTrustMetadata()));
    }
    if (dto.getOriginTrustMetadata() != null) {
      builder.setOriginTrustMetadata(toProto(dto.getOriginTrustMetadata()));
    }
  }

  private static void setCommonTriggerFields(
      EventSignalTriggerMessage.Builder builder, ProcessInstanceTriggerDTO dto) {
    if (dto.getProcessInstanceId() != null) {
      builder.setProcessInstanceId(toProto(dto.getProcessInstanceId()));
    }
    if (dto.getCurrentTrustMetadata() != null) {
      builder.setCurrentTrustMetadata(toProto(dto.getCurrentTrustMetadata()));
    }
    if (dto.getOriginTrustMetadata() != null) {
      builder.setOriginTrustMetadata(toProto(dto.getOriginTrustMetadata()));
    }
  }

  private static void setCommonTriggerFields(
      ExternalTaskResponseTriggerMessage.Builder builder, ProcessInstanceTriggerDTO dto) {
    if (dto.getProcessInstanceId() != null) {
      builder.setProcessInstanceId(toProto(dto.getProcessInstanceId()));
    }
    if (dto.getCurrentTrustMetadata() != null) {
      builder.setCurrentTrustMetadata(toProto(dto.getCurrentTrustMetadata()));
    }
    if (dto.getOriginTrustMetadata() != null) {
      builder.setOriginTrustMetadata(toProto(dto.getOriginTrustMetadata()));
    }
  }

  private static void setCommonTriggerFields(
      UserTaskResponseTriggerMessage.Builder builder, ProcessInstanceTriggerDTO dto) {
    if (dto.getProcessInstanceId() != null) {
      builder.setProcessInstanceId(toProto(dto.getProcessInstanceId()));
    }
    if (dto.getCurrentTrustMetadata() != null) {
      builder.setCurrentTrustMetadata(toProto(dto.getCurrentTrustMetadata()));
    }
    if (dto.getOriginTrustMetadata() != null) {
      builder.setOriginTrustMetadata(toProto(dto.getOriginTrustMetadata()));
    }
  }

  private static void applyTrustMetadata(
      ProcessInstanceTriggerDTO dto,
      CommandTrustMetadataDTO currentTrustMetadata,
      CommandTrustMetadataDTO originTrustMetadata) {
    dto.setCurrentTrustMetadata(currentTrustMetadata);
    dto.setOriginTrustMetadata(originTrustMetadata);
  }

  private static void setBaseEventSignalFields(
      MessageEventSignalMessage.Builder builder, EventSignalDTO dto) {
    setCommonEventSignalFields(builder, dto);
  }

  private static void setBaseEventSignalFields(
      ErrorEventSignalMessage.Builder builder, EventSignalDTO dto) {
    setCommonEventSignalFields(builder, dto);
  }

  private static void setBaseEventSignalFields(
      EscalationEventSignalMessage.Builder builder, EventSignalDTO dto) {
    setCommonEventSignalFields(builder, dto);
  }

  private static void setBaseEventSignalFields(
      TimerEventSignalMessage.Builder builder, EventSignalDTO dto) {
    setCommonEventSignalFields(builder, dto);
  }

  private static void setBaseEventSignalFields(
      SignalEventSignalMessage.Builder builder, EventSignalDTO dto) {
    setCommonEventSignalFields(builder, dto);
  }

  private static void setCommonEventSignalFields(
      MessageEventSignalMessage.Builder builder, EventSignalDTO dto) {
    if (dto.getElementInstanceIdPath() != null) {
      builder.addAllElementInstanceIdPath(dto.getElementInstanceIdPath());
    }
    if (dto.getVariables() != null) {
      builder.setVariables(toProto(dto.getVariables()));
    }
  }

  private static void setCommonEventSignalFields(
      ErrorEventSignalMessage.Builder builder, EventSignalDTO dto) {
    if (dto.getElementInstanceIdPath() != null) {
      builder.addAllElementInstanceIdPath(dto.getElementInstanceIdPath());
    }
    if (dto.getVariables() != null) {
      builder.setVariables(toProto(dto.getVariables()));
    }
  }

  private static void setCommonEventSignalFields(
      EscalationEventSignalMessage.Builder builder, EventSignalDTO dto) {
    if (dto.getElementInstanceIdPath() != null) {
      builder.addAllElementInstanceIdPath(dto.getElementInstanceIdPath());
    }
    if (dto.getVariables() != null) {
      builder.setVariables(toProto(dto.getVariables()));
    }
  }

  private static void setCommonEventSignalFields(
      TimerEventSignalMessage.Builder builder, EventSignalDTO dto) {
    if (dto.getElementInstanceIdPath() != null) {
      builder.addAllElementInstanceIdPath(dto.getElementInstanceIdPath());
    }
    if (dto.getVariables() != null) {
      builder.setVariables(toProto(dto.getVariables()));
    }
  }

  private static void setCommonEventSignalFields(
      SignalEventSignalMessage.Builder builder, EventSignalDTO dto) {
    if (dto.getElementInstanceIdPath() != null) {
      builder.addAllElementInstanceIdPath(dto.getElementInstanceIdPath());
    }
    if (dto.getVariables() != null) {
      builder.setVariables(toProto(dto.getVariables()));
    }
  }

  private static void applyBaseEventSignal(
      EventSignalDTO dto, List<Long> elementInstanceIdPath, VariablesDTO variables) {
    dto.setElementInstanceIdPath(new ArrayList<>(elementInstanceIdPath));
    dto.setVariables(variables);
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

  private static io.taktx.proto.ExternalTaskResponseType toProto(
      ExternalTaskResponseType responseType) {
    return switch (responseType) {
      case SUCCESS -> io.taktx.proto.ExternalTaskResponseType.EXTERNAL_TASK_RESPONSE_SUCCESS;
      case PROMISE -> io.taktx.proto.ExternalTaskResponseType.EXTERNAL_TASK_RESPONSE_PROMISE;
      case TIMEOUT -> io.taktx.proto.ExternalTaskResponseType.EXTERNAL_TASK_RESPONSE_TIMEOUT;
      case ESCALATION -> io.taktx.proto.ExternalTaskResponseType.EXTERNAL_TASK_RESPONSE_ESCALATION;
      case ERROR -> io.taktx.proto.ExternalTaskResponseType.EXTERNAL_TASK_RESPONSE_ERROR;
      case INCIDENT -> io.taktx.proto.ExternalTaskResponseType.EXTERNAL_TASK_RESPONSE_INCIDENT;
    };
  }

  private static ExternalTaskResponseType toDto(
      io.taktx.proto.ExternalTaskResponseType responseType) {
    return switch (responseType) {
      case EXTERNAL_TASK_RESPONSE_SUCCESS -> ExternalTaskResponseType.SUCCESS;
      case EXTERNAL_TASK_RESPONSE_PROMISE -> ExternalTaskResponseType.PROMISE;
      case EXTERNAL_TASK_RESPONSE_TIMEOUT -> ExternalTaskResponseType.TIMEOUT;
      case EXTERNAL_TASK_RESPONSE_ESCALATION -> ExternalTaskResponseType.ESCALATION;
      case EXTERNAL_TASK_RESPONSE_ERROR -> ExternalTaskResponseType.ERROR;
      case EXTERNAL_TASK_RESPONSE_INCIDENT -> ExternalTaskResponseType.INCIDENT;
      case EXTERNAL_TASK_RESPONSE_UNSPECIFIED, UNRECOGNIZED -> null;
    };
  }

  private static io.taktx.proto.UserTaskResponseType toProto(UserTaskResponseType responseType) {
    return switch (responseType) {
      case COMPLETED -> io.taktx.proto.UserTaskResponseType.USER_TASK_RESPONSE_COMPLETED;
      case ESCALATION -> io.taktx.proto.UserTaskResponseType.USER_TASK_RESPONSE_ESCALATION;
      case ERROR -> io.taktx.proto.UserTaskResponseType.USER_TASK_RESPONSE_ERROR;
    };
  }

  private static UserTaskResponseType toDto(io.taktx.proto.UserTaskResponseType responseType) {
    return switch (responseType) {
      case USER_TASK_RESPONSE_COMPLETED -> UserTaskResponseType.COMPLETED;
      case USER_TASK_RESPONSE_ESCALATION -> UserTaskResponseType.ESCALATION;
      case USER_TASK_RESPONSE_ERROR -> UserTaskResponseType.ERROR;
      case USER_TASK_RESPONSE_UNSPECIFIED, UNRECOGNIZED -> null;
    };
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
      List<IoVariableMappingMessage> messages) {
    if (messages == null || messages.isEmpty()) {
      return Set.of();
    }
    LinkedHashSet<IoVariableMappingDTO> result = new LinkedHashSet<>();
    for (IoVariableMappingMessage message : messages) {
      result.add(
          new IoVariableMappingDTO(
              emptyToNull(message.getSource()), emptyToNull(message.getTarget())));
    }
    return result;
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
    return VarMap.newBuilder()
        .putAllEntries(VariableValueDtoMapper.toVariableMap(variables))
        .build();
  }

  private static VariablesDTO toVariablesDto(VarMap variables) {
    Map<String, io.taktx.proto.VariableValue> entries =
        variables == null ? Map.of() : variables.getEntriesMap();
    return VariableValueDtoMapper.toVariablesDto(entries);
  }

  private static String emptyToNull(String value) {
    return value == null || value.isEmpty() ? null : value;
  }
}
