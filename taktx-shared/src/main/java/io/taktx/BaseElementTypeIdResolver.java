/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.DatabindContext;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.jsontype.impl.TypeIdResolverBase;
import io.taktx.dto.BoundaryEventDTO;
import io.taktx.dto.CallActivityDTO;
import io.taktx.dto.EndEventDTO;
import io.taktx.dto.ErrorEventDefinitionDTO;
import io.taktx.dto.EscalationEventDefinitionDTO;
import io.taktx.dto.EventBasedGatewayDTO;
import io.taktx.dto.ExclusiveGatewayDTO;
import io.taktx.dto.InclusiveGatewayDTO;
import io.taktx.dto.IntermediateCatchEventDTO;
import io.taktx.dto.IntermediateThrowEventDTO;
import io.taktx.dto.LinkEventDefinitionDTO;
import io.taktx.dto.MessageEndEventDTO;
import io.taktx.dto.MessageEventDefinitionDTO;
import io.taktx.dto.MessageIntermediateThrowEventDTO;
import io.taktx.dto.ParallelGatewayDTO;
import io.taktx.dto.ProcessDTO;
import io.taktx.dto.ReceiveTaskDTO;
import io.taktx.dto.ScriptTaskDTO;
import io.taktx.dto.SendTaskDTO;
import io.taktx.dto.SequenceFlowDTO;
import io.taktx.dto.ServiceTaskDTO;
import io.taktx.dto.SignalEventDefinitionDTO;
import io.taktx.dto.StartEventDTO;
import io.taktx.dto.SubProcessDTO;
import io.taktx.dto.TaskDTO;
import io.taktx.dto.TerminateEventDefinitionDTO;
import io.taktx.dto.TimerEventDefinitionDTO;
import io.taktx.dto.UserTaskDTO;

public class BaseElementTypeIdResolver extends TypeIdResolverBase {

  @Override
  public String idFromValue(Object value) {
    return switch (value) {
      case BoundaryEventDTO ignored -> "B";
      case StartEventDTO ignored -> "S";
      case IntermediateCatchEventDTO ignored -> "IC";
      case IntermediateThrowEventDTO ignored -> "IT";
      case EndEventDTO ignored -> "E";
      case InclusiveGatewayDTO ignored -> "IG";
      case EventBasedGatewayDTO ignored -> "VG";
      case ParallelGatewayDTO ignored -> "PG";
      case ExclusiveGatewayDTO ignored -> "EG";
      case SubProcessDTO ignored -> "SP";
      case CallActivityDTO ignored -> "CA";
      case ReceiveTaskDTO ignored -> "RT";
      case SendTaskDTO ignored -> "ST";
      case ServiceTaskDTO ignored -> "SV";
      case MessageEndEventDTO ignored -> "MS";
      case MessageIntermediateThrowEventDTO ignored -> "MI";
      case ScriptTaskDTO ignored -> "SC";
      case UserTaskDTO ignored -> "UT";
      case TaskDTO ignored -> "T";
      case SequenceFlowDTO ignored -> "Q";
      case ProcessDTO ignored -> "P";
      case LinkEventDefinitionDTO ignored -> "LE";
      case TerminateEventDefinitionDTO ignored -> "TE";
      case EscalationEventDefinitionDTO ignored -> "ES";
      case TimerEventDefinitionDTO ignored -> "TM";
      case ErrorEventDefinitionDTO ignored -> "ER";
      case MessageEventDefinitionDTO ignored -> "ME";
      case SignalEventDefinitionDTO ignored -> "SE";
      default -> throw new IllegalStateException("Unknown type: " + value.getClass());
    };
  }

  @Override
  public String idFromValueAndType(Object o, Class<?> suggestedType) {
    return idFromValue(o);
  }

  @Override
  public JsonTypeInfo.Id getMechanism() {
    return JsonTypeInfo.Id.CUSTOM;
  }

  @Override
  public JavaType typeFromId(DatabindContext context, String id) {
    return switch (id) {
      case "B" -> context.constructType(BoundaryEventDTO.class);
      case "S" -> context.constructType(StartEventDTO.class);
      case "IC" -> context.constructType(IntermediateCatchEventDTO.class);
      case "IT" -> context.constructType(IntermediateThrowEventDTO.class);
      case "E" -> context.constructType(EndEventDTO.class);
      case "IG" -> context.constructType(InclusiveGatewayDTO.class);
      case "VG" -> context.constructType(EventBasedGatewayDTO.class);
      case "PG" -> context.constructType(ParallelGatewayDTO.class);
      case "EG" -> context.constructType(ExclusiveGatewayDTO.class);
      case "SP" -> context.constructType(SubProcessDTO.class);
      case "CA" -> context.constructType(CallActivityDTO.class);
      case "RT" -> context.constructType(ReceiveTaskDTO.class);
      case "ST" -> context.constructType(SendTaskDTO.class);
      case "MS" -> context.constructType(MessageEndEventDTO.class);
      case "MI" -> context.constructType(MessageIntermediateThrowEventDTO.class);
      case "SC" -> context.constructType(ScriptTaskDTO.class);
      case "SV" -> context.constructType(ServiceTaskDTO.class);
      case "UT" -> context.constructType(UserTaskDTO.class);
      case "T" -> context.constructType(TaskDTO.class);
      case "Q" -> context.constructType(SequenceFlowDTO.class);
      case "P" -> context.constructType(ProcessDTO.class);
      case "LE" -> context.constructType(LinkEventDefinitionDTO.class);
      case "TE" -> context.constructType(TerminateEventDefinitionDTO.class);
      case "ES" -> context.constructType(EscalationEventDefinitionDTO.class);
      case "TM" -> context.constructType(TimerEventDefinitionDTO.class);
      case "ER" -> context.constructType(ErrorEventDefinitionDTO.class);
      case "ME" -> context.constructType(MessageEventDefinitionDTO.class);
      case "SE" -> context.constructType(SignalEventDefinitionDTO.class);
      default -> throw new IllegalStateException("Unknown type: " + id);
    };
  }
}
