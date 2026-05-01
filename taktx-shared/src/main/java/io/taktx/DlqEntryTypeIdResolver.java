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
import io.taktx.dto.MessageEventDlqEntryDTO;
import io.taktx.dto.ProcessDefinitionDlqEntryDTO;
import io.taktx.dto.ProcessInstanceDlqEntryDTO;
import io.taktx.dto.SignalDlqEntryDTO;
import io.taktx.dto.UserTaskResponseDlqEntryDTO;

public class DlqEntryTypeIdResolver extends TypeIdResolverBase {

  @Override
  public String idFromValue(Object value) {
    return switch (value) {
      case ProcessDefinitionDlqEntryDTO ignored -> "D";
      case ProcessInstanceDlqEntryDTO ignored -> "I";
      case MessageEventDlqEntryDTO ignored -> "M";
      case SignalDlqEntryDTO ignored -> "S";
      case UserTaskResponseDlqEntryDTO ignored -> "U";
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
      case "D" -> context.constructType(ProcessDefinitionDlqEntryDTO.class);
      case "I" -> context.constructType(ProcessInstanceDlqEntryDTO.class);
      case "M" -> context.constructType(MessageEventDlqEntryDTO.class);
      case "S" -> context.constructType(SignalDlqEntryDTO.class);
      case "U" -> context.constructType(UserTaskResponseDlqEntryDTO.class);
      default -> throw new IllegalStateException("Unknown type: " + id);
    };
  }
}
