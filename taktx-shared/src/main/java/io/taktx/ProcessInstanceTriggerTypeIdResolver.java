/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks All rights reserved.
 * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 * Free use is permitted with up to 3 Kafka partitions per topic. See LICENSE file for details.
 * For commercial use or more partitions and features, contact [https://www.taktx.io/contact].
 */

package io.taktx;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.DatabindContext;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.jsontype.impl.TypeIdResolverBase;
import io.taktx.dto.AbortTriggerDTO;
import io.taktx.dto.ContinueFlowElementTriggerDTO;
import io.taktx.dto.EventSignalTriggerDTO;
import io.taktx.dto.ExternalTaskResponseTriggerDTO;
import io.taktx.dto.ExternalTaskTriggerDTO;
import io.taktx.dto.StartCommandDTO;
import io.taktx.dto.StartFlowElementTriggerDTO;
import io.taktx.dto.UserTaskResponseTriggerDTO;

public class ProcessInstanceTriggerTypeIdResolver extends TypeIdResolverBase {

  @Override
  public String idFromValue(Object value) {
    return switch (value) {
      case EventSignalTriggerDTO ignored -> "V";
      case ExternalTaskTriggerDTO ignored -> "E";
      case StartFlowElementTriggerDTO ignored -> "S";
      case AbortTriggerDTO ignored -> "T";
      case ExternalTaskResponseTriggerDTO ignored -> "R";
      case UserTaskResponseTriggerDTO ignored -> "U";
      case ContinueFlowElementTriggerDTO ignored -> "C";
      case StartCommandDTO ignored -> "A";
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
      case "V" -> context.constructType(EventSignalTriggerDTO.class);
      case "E" -> context.constructType(ExternalTaskTriggerDTO.class);
      case "S" -> context.constructType(StartFlowElementTriggerDTO.class);
      case "T" -> context.constructType(AbortTriggerDTO.class);
      case "R" -> context.constructType(ExternalTaskResponseTriggerDTO.class);
      case "U" -> context.constructType(UserTaskResponseTriggerDTO.class);
      case "C" -> context.constructType(ContinueFlowElementTriggerDTO.class);
      case "A" -> context.constructType(StartCommandDTO.class);
      default -> throw new IllegalStateException("Unknown type: " + id);
    };
  }
}
