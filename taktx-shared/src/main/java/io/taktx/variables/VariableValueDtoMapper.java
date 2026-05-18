/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.variables;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.taktx.dto.VariablesDTO;
import io.taktx.proto.VariableValue;
import java.util.HashMap;
import java.util.Map;

/** Temporary bridge between legacy DTOs and proto-backed variable values. */
public class VariableValueDtoMapper {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  public VariableValueDtoMapper() {}

  public static VariableValue toVariableValue(JsonNode node) {
    if (node == null || node.isNull()) {
      return Variables.nullValue();
    }
    try {
      return Variables.of(OBJECT_MAPPER.treeToValue(node, Object.class));
    } catch (Exception e) {
      throw new IllegalStateException("Failed to convert JsonNode to VariableValue", e);
    }
  }

  public static JsonNode toJsonNode(VariableValue value) {
    return OBJECT_MAPPER.valueToTree(Variables.toJavaObject(value));
  }

  public static Map<String, VariableValue> toVariableMap(VariablesDTO variables) {
    Map<String, VariableValue> result = new HashMap<>();
    if (variables == null || variables.getVariables() == null) {
      return result;
    }
    variables.getVariables().forEach((key, value) -> result.put(key, toVariableValue(value)));
    return result;
  }

  public static VariablesDTO toVariablesDto(Map<String, VariableValue> variables) {
    Map<String, JsonNode> result = new HashMap<>();
    if (variables != null) {
      variables.forEach((key, value) -> result.put(key, toJsonNode(value)));
    }
    return VariablesDTO.ofJsonMap(result);
  }

  public static VariablesDTO emptyVariables() {
    return VariablesDTO.empty();
  }
}
