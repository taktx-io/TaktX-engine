package com.flomaestro.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flomaestro.takt.dto.v_1_0_0.ExternalTaskTriggerDTO;

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
