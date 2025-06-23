/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks All rights reserved.
 * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 * Free use is permitted with up to 3 Kafka partitions per topic. See LICENSE file for details.
 * For commercial use or more partitions and features, contact [https://www.taktx.io/contact].
 */

package io.taktx.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.taktx.dto.ExternalTaskTriggerDTO;

public class VariableParameterResolver implements TaktParameterResolver {

  private final ObjectMapper objectMapper;
  private final Class<?> type;
  private final String name;

  public VariableParameterResolver(ObjectMapper objectMapper, Class<?> type, String name) {
    this.objectMapper = objectMapper;
    this.type = type;
    this.name = name;
  }

  @Override
  public Object resolve(ExternalTaskTriggerDTO externalTaskTriggerDTO) {
    JsonNode jsonNode = externalTaskTriggerDTO.getVariables().get(name);
    if (jsonNode != null) {
      return objectMapper.convertValue(jsonNode, type);
    }
    return null;
  }
}
