package com.flomaestro.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flomaestro.takt.dto.v_1_0_0.ExternalTaskTriggerDTO;
import java.util.HashMap;
import java.util.Map;

public class MapParameterResolver implements TaktParameterResolver {

  private final ObjectMapper mapper;

  public MapParameterResolver(ObjectMapper mapper) {
    this.mapper = mapper;
  }

  @Override
  public Object resolve(ExternalTaskTriggerDTO externalTaskTriggerDTO) {
    Map<String, Object> result = new HashMap<>();

    externalTaskTriggerDTO
        .getVariables()
        .getVariables()
        .forEach(
            (key, value) ->
              result.put(key, mapper.convertValue(value, Object.class))
            );

    return result;
  }
}
