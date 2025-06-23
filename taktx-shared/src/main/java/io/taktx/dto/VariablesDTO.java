/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks All rights reserved.
 * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 * Free use is permitted with up to 3 Kafka partitions per topic. See LICENSE file for details.
 * For commercial use or more partitions and features, contact [https://www.taktx.io/contact].
 */

package io.taktx.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonFormat.Shape;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.cbor.CBORFactory;
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

  public static VariablesDTO of(Map<String, JsonNode> variables) {
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

  public VariablesDTO diff(VariablesDTO updatedVariablesAtStart) {
    Map<String, JsonNode> diff = new HashMap<>();
    variables.forEach(
        (key, value) -> {
          JsonNode startValue = updatedVariablesAtStart.get(key);
          if (startValue == null || !startValue.equals(value)) {
            diff.put(key, value);
          }
        });

    return new VariablesDTO(diff);
  }
}
