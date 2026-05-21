/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.dto;

import io.taktx.proto.VariableValue;
import io.taktx.variables.Variables;
import java.util.HashMap;
import java.util.Map;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Getter
@ToString
@NoArgsConstructor
@EqualsAndHashCode
public class VariablesDTO {

  private Map<String, VariableValue> variables = new HashMap<>();

  public VariablesDTO(Map<String, VariableValue> variables) {
    setVariables(variables);
  }

  public void setVariables(Map<String, VariableValue> variables) {
    this.variables = normalizeVariables(variables);
  }

  public static VariablesDTO empty() {
    return new VariablesDTO(Map.of());
  }

  public static VariablesDTO of(String key, Object value) {
    return new VariablesDTO(Map.of(key, Variables.of(value)));
  }

  public static VariablesDTO ofObjectMap(Map<String, Object> variables) {
    Map<String, VariableValue> variablesMap = new HashMap<>();
    if (variables != null) {
      variables.forEach((key, value) -> variablesMap.put(key, Variables.of(value)));
    }
    return new VariablesDTO(variablesMap);
  }

  public static VariablesDTO ofVariableMap(Map<String, VariableValue> variables) {
    return new VariablesDTO(variables);
  }

  public static VariablesDTO of(String key, Object value, String key2, Object value2) {
    return new VariablesDTO(Map.of(key, Variables.of(value), key2, Variables.of(value2)));
  }

  public static VariablesDTO of(
      String key, Object value, String key2, Object value2, String key3, Object value3) {
    return new VariablesDTO(
        Map.of(key, Variables.of(value), key2, Variables.of(value2), key3, Variables.of(value3)));
  }

  public void put(String key, VariableValue value) {
    variables.put(key, normalize(value));
  }

  public VariableValue get(String key) {
    return variables.get(key);
  }

  public boolean containsKey(String key) {
    return variables.containsKey(key);
  }

  private static Map<String, VariableValue> normalizeVariables(
      Map<String, VariableValue> variables) {
    HashMap<String, VariableValue> normalized = new HashMap<>();
    if (variables != null) {
      variables.forEach((key, value) -> normalized.put(key, normalize(value)));
    }
    return normalized;
  }

  private static VariableValue normalize(VariableValue value) {
    if (value != null) {
      value.getSerializedSize();
    }
    return value;
  }
}
