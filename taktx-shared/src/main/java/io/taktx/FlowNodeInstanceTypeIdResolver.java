/*
 *
 *  * TaktX - A high-performance BPMN engine
 *  * Copyright (c) 2025 TaktX B.V. All rights reserved.
 *  * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 *  * Free use is permitted with up to 3 Kafka partitions. See LICENSE file for details.
 *  * For commercial use or more partitions and features, contact [info@taktx.io] or [https://www.taktx.io/contact].
 *
 */

package io.taktx;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.DatabindContext;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.jsontype.impl.TypeIdResolverBase;
import io.taktx.dto.BoundaryEventInstanceDTO;
import io.taktx.dto.CallActivityInstanceDTO;
import io.taktx.dto.EndEventInstanceDTO;
import io.taktx.dto.ExclusiveGatewayInstanceDTO;
import io.taktx.dto.InclusiveGatewayInstanceDTO;
import io.taktx.dto.IntermediateCatchEventInstanceDTO;
import io.taktx.dto.IntermediateThrowEventInstanceDTO;
import io.taktx.dto.MultiInstanceInstanceDTO;
import io.taktx.dto.ParallelGatewayInstanceDTO;
import io.taktx.dto.ReceiveTaskInstanceDTO;
import io.taktx.dto.SendTaskInstanceDTO;
import io.taktx.dto.ServiceTaskInstanceDTO;
import io.taktx.dto.StartEventInstanceDTO;
import io.taktx.dto.SubProcessInstanceDTO;
import io.taktx.dto.TaskInstanceDTO;

public class FlowNodeInstanceTypeIdResolver extends TypeIdResolverBase {

  @Override
  public String idFromValue(Object value) {
    return switch (value) {
      case CallActivityInstanceDTO ignored -> "C";
      case ReceiveTaskInstanceDTO ignored -> "R";
      case SendTaskInstanceDTO ignored -> "D";
      case ServiceTaskInstanceDTO ignored -> "V";
      case TaskInstanceDTO ignored -> "T";
      case SubProcessInstanceDTO ignored -> "S";
      case MultiInstanceInstanceDTO ignored -> "M";
      case EndEventInstanceDTO ignored -> "E";
      case IntermediateThrowEventInstanceDTO ignored -> "W";
      case IntermediateCatchEventInstanceDTO ignored -> "I";
      case StartEventInstanceDTO ignored -> "A";
      case BoundaryEventInstanceDTO ignored -> "B";
      case InclusiveGatewayInstanceDTO ignored -> "N";
      case ParallelGatewayInstanceDTO ignored -> "P";
      case ExclusiveGatewayInstanceDTO ignored -> "X";
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
      case "C" -> context.constructType(CallActivityInstanceDTO.class);
      case "R" -> context.constructType(ReceiveTaskInstanceDTO.class);
      case "D" -> context.constructType(SendTaskInstanceDTO.class);
      case "V" -> context.constructType(ServiceTaskInstanceDTO.class);
      case "T" -> context.constructType(TaskInstanceDTO.class);
      case "S" -> context.constructType(SubProcessInstanceDTO.class);
      case "M" -> context.constructType(MultiInstanceInstanceDTO.class);
      case "E" -> context.constructType(EndEventInstanceDTO.class);
      case "W" -> context.constructType(IntermediateThrowEventInstanceDTO.class);
      case "I" -> context.constructType(IntermediateCatchEventInstanceDTO.class);
      case "A" -> context.constructType(StartEventInstanceDTO.class);
      case "B" -> context.constructType(BoundaryEventInstanceDTO.class);
      case "N" -> context.constructType(InclusiveGatewayInstanceDTO.class);
      case "P" -> context.constructType(ParallelGatewayInstanceDTO.class);
      case "X" -> context.constructType(ExclusiveGatewayInstanceDTO.class);
      default -> throw new IllegalStateException("Unknown type: " + id);
    };
  }
}
