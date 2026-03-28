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
import io.taktx.dto.ErrorEventSignalDTO;
import io.taktx.dto.EscalationEventSignalDTO;
import io.taktx.dto.MessageEventSignalDTO;
import io.taktx.dto.SignalEventSignalDTO;
import io.taktx.dto.TimerEventSignalDTO;

public class EventSignalTypeIdResolver extends TypeIdResolverBase {

  @Override
  public String idFromValue(Object value) {
    return switch (value) {
      case MessageEventSignalDTO ignored -> "M";
      case ErrorEventSignalDTO ignored -> "R";
      case EscalationEventSignalDTO ignored -> "S";
      case TimerEventSignalDTO ignored -> "T";
      case SignalEventSignalDTO ignored -> "I";
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
      case "M" -> context.constructType(MessageEventSignalDTO.class);
      case "R" -> context.constructType(ErrorEventSignalDTO.class);
      case "S" -> context.constructType(EscalationEventSignalDTO.class);
      case "T" -> context.constructType(TimerEventSignalDTO.class);
      case "I" -> context.constructType(SignalEventSignalDTO.class);
      default -> throw new IllegalStateException("Unknown type: " + id);
    };
  }
}
