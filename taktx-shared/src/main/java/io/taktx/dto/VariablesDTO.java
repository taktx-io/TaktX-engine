/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonFormat.Shape;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.cbor.CBORFactory;
import io.quarkus.runtime.annotations.RegisterForReflection;
import java.util.HashMap;
import java.util.Map;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
@NoArgsConstructor
@EqualsAndHashCode
@JsonFormat(shape = Shape.ARRAY)
@RegisterForReflection
public class VariablesDTO {

  public static final ObjectMapper OBJECT_MAPPER = new ObjectMapper(new CBORFactory());

  private Map<String, JsonNode> variables;

  public VariablesDTO(Map<String, JsonNode> variables) {
    this.variables = new HashMap<>(variables);
  }

  public static VariablesDTO empty() {
    return new VariablesDTO(Map.of());
  }

  public static VariablesDTO of(String key, Object value) {
    return new VariablesDTO(Map.of(key, OBJECT_MAPPER.valueToTree(value)));
  }

  public static VariablesDTO ofObjectMap(Map<String, Object> variables) {
    Map<String, JsonNode> variablesMap = OBJECT_MAPPER.convertValue(variables, Map.class);
    return new VariablesDTO(variablesMap);
  }

  public static VariablesDTO ofJsonMap(Map<String, JsonNode> variables) {
    return new VariablesDTO(variables);
  }

  public static VariablesDTO of(String key, Object value, String key2, Object value2) {
    return new VariablesDTO(
        Map.of(key, OBJECT_MAPPER.valueToTree(value), key2, OBJECT_MAPPER.valueToTree(value2)));
  }

  public static VariablesDTO of(
      String key, Object value, String key2, Object value2, String key3, Object value3) {
    JsonNode v1 = OBJECT_MAPPER.valueToTree(value);
    JsonNode v2 = OBJECT_MAPPER.valueToTree(value2);
    JsonNode v3 = OBJECT_MAPPER.valueToTree(value3);
    return new VariablesDTO(Map.of(key, v1, key2, v2, key3, v3));
  }

  public void put(String key, JsonNode value) {
    variables.put(key, value);
  }

  @JsonIgnore
  public JsonNode get(String key) {
    return variables.get(key);
  }

  @JsonIgnore
  public boolean containsKey(String key) {
    return variables.containsKey(key);
  }
}
