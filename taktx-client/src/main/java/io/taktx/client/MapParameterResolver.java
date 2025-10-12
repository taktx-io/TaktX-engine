/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks All rights reserved.
 * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 * Free use is permitted with up to 3 Kafka partitions per topic. See LICENSE file for details.
 * For commercial use or more partitions and features, contact [https://www.taktx.io/contact].
 */

package io.taktx.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.taktx.dto.ExternalTaskTriggerDTO;
import java.util.HashMap;
import java.util.Map;

/** A parameter resolver that converts the variables of an ExternalTaskTriggerDTO into a Map. */
public class MapParameterResolver implements TaktParameterResolver {

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
