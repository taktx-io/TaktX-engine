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

/**
 * A parameter resolver that extracts a variable from the ExternalTaskTriggerDTO's variables map and
 * converts it to the specified type using Jackson's ObjectMapper.
 */
public class VariableParameterResolver implements TaktParameterResolver {

  private final ObjectMapper objectMapper;
  private final Class<?> type;
  private final String name;

  /**
   * Constructs a VariableParameterResolver.
   *
   * @param objectMapper the ObjectMapper to use for conversion
   * @param type the target type to convert the variable to
   * @param name the name of the variable to extract
   */
  public VariableParameterResolver(ObjectMapper objectMapper, Class<?> type, String name) {
    this.objectMapper = objectMapper;
    this.type = type;
    this.name = name;
  }

  /**
   * Resolves the parameter by extracting the variable from the ExternalTaskTriggerDTO and
   * converting it to the specified type.
   *
   * @param externalTaskTriggerDTO the ExternalTaskTriggerDTO containing the variables
   * @return the resolved parameter value, or null if the variable is not found
   */
  @Override
  public Object resolve(ExternalTaskTriggerDTO externalTaskTriggerDTO) {
    JsonNode jsonNode = externalTaskTriggerDTO.getVariables().get(name);
    if (jsonNode != null) {
      return objectMapper.convertValue(jsonNode, type);
    }
    return null;
  }
}
