/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.taktx.dto.ExternalTaskTriggerDTO;
import java.util.HashMap;
import java.util.Map;

/** A parameter resolver that converts the variables of an ExternalTaskTriggerDTO into a Map. */
public class MapParameterResolver implements ParameterResolver {

  private final ObjectMapper mapper;

  /**
   * Constructor for MapParameterResolver.
   *
   * @param mapper The ObjectMapper used for converting variable values.
   */
  public MapParameterResolver(ObjectMapper mapper) {
    this.mapper = mapper;
  }

  @Override
  public Object resolve(ExternalTaskTriggerDTO externalTaskTriggerDTO) {
    Map<String, Object> result = new HashMap<>();

    externalTaskTriggerDTO
        .getVariables()
        .getVariables()
        .forEach((key, value) -> result.put(key, mapper.convertValue(value, Object.class)));

    return result;
  }
}
