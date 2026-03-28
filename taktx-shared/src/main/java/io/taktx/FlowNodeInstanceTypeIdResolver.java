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
import io.taktx.dto.BoundaryEventInstanceDTO;
import io.taktx.dto.CallActivityInstanceDTO;
import io.taktx.dto.EndEventInstanceDTO;
import io.taktx.dto.EventBasedGatewayInstanceDTO;
import io.taktx.dto.ExclusiveGatewayInstanceDTO;
import io.taktx.dto.InclusiveGatewayInstanceDTO;
import io.taktx.dto.IntermediateCatchEventInstanceDTO;
import io.taktx.dto.IntermediateThrowEventInstanceDTO;
import io.taktx.dto.MessageEndEventInstanceDTO;
import io.taktx.dto.MessageIntermediateThrowEventInstanceDTO;
import io.taktx.dto.MultiInstanceInstanceDTO;
import io.taktx.dto.ParallelGatewayInstanceDTO;
import io.taktx.dto.ReceiveTaskInstanceDTO;
import io.taktx.dto.ScriptTaskInstanceDTO;
import io.taktx.dto.SendTaskInstanceDTO;
import io.taktx.dto.ServiceTaskInstanceDTO;
import io.taktx.dto.StartEventInstanceDTO;
import io.taktx.dto.SubProcessInstanceDTO;
import io.taktx.dto.TaskInstanceDTO;
import io.taktx.dto.UserTaskInstanceDTO;

public class FlowNodeInstanceTypeIdResolver extends TypeIdResolverBase {

  @Override
  public String idFromValue(Object value) {
    return switch (value) {
      case StartEventInstanceDTO ignored -> "A";
      case BoundaryEventInstanceDTO ignored -> "B";
      case CallActivityInstanceDTO ignored -> "C";
      case SendTaskInstanceDTO ignored -> "D";
      case EndEventInstanceDTO ignored -> "E";
      case ScriptTaskInstanceDTO ignored -> "F";
      case MessageEndEventInstanceDTO ignored -> "G";
      case MessageIntermediateThrowEventInstanceDTO ignored -> "H";
      case IntermediateCatchEventInstanceDTO ignored -> "I";
      case EventBasedGatewayInstanceDTO ignored -> "J";
      case MultiInstanceInstanceDTO ignored -> "M";
      case InclusiveGatewayInstanceDTO ignored -> "N";
      case ParallelGatewayInstanceDTO ignored -> "P";
      case ReceiveTaskInstanceDTO ignored -> "R";
      case SubProcessInstanceDTO ignored -> "S";
      case UserTaskInstanceDTO ignored -> "U";
      case ServiceTaskInstanceDTO ignored -> "V";
      case IntermediateThrowEventInstanceDTO ignored -> "W";
      case ExclusiveGatewayInstanceDTO ignored -> "X";
      case TaskInstanceDTO ignored -> "T";
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
      case "A" -> context.constructType(StartEventInstanceDTO.class);
      case "B" -> context.constructType(BoundaryEventInstanceDTO.class);
      case "C" -> context.constructType(CallActivityInstanceDTO.class);
      case "D" -> context.constructType(SendTaskInstanceDTO.class);
      case "E" -> context.constructType(EndEventInstanceDTO.class);
      case "F" -> context.constructType(ScriptTaskInstanceDTO.class);
      case "G" -> context.constructType(MessageEndEventInstanceDTO.class);
      case "H" -> context.constructType(MessageIntermediateThrowEventInstanceDTO.class);
      case "I" -> context.constructType(IntermediateCatchEventInstanceDTO.class);
      case "J" -> context.constructType(EventBasedGatewayInstanceDTO.class);
      case "M" -> context.constructType(MultiInstanceInstanceDTO.class);
      case "N" -> context.constructType(InclusiveGatewayInstanceDTO.class);
      case "P" -> context.constructType(ParallelGatewayInstanceDTO.class);
      case "R" -> context.constructType(ReceiveTaskInstanceDTO.class);
      case "S" -> context.constructType(SubProcessInstanceDTO.class);
      case "U" -> context.constructType(UserTaskInstanceDTO.class);
      case "V" -> context.constructType(ServiceTaskInstanceDTO.class);
      case "W" -> context.constructType(IntermediateThrowEventInstanceDTO.class);
      case "X" -> context.constructType(ExclusiveGatewayInstanceDTO.class);
      case "T" -> context.constructType(TaskInstanceDTO.class);
      default -> throw new IllegalStateException("Unknown type: " + id);
    };
  }
}
