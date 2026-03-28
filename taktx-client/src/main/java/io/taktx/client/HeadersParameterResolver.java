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
import java.util.Map;

/**
 * Resolver for parameters annotated with @CustomHeaders. It maps the headers from process instance
 * to the desired type.
 */
public class HeadersParameterResolver implements ParameterResolver {

  private final ObjectMapper objectMapper;
  private final Class<?> type;

  /**
   * Constructs a HeadersParameterResolver.
   *
   * @param objectMapper the ObjectMapper for mapping
   * @param type the target type for the headers
   */
  public HeadersParameterResolver(ObjectMapper objectMapper, Class<?> type) {
    this.objectMapper = objectMapper;
    this.type = type;
  }

  /**
   * Resolves the parameter by extracting headers.
   *
   * @param externalTaskTriggerDTO The DTO containing data for resolution.
   * @return The resolved headers in the desired type.
   */
  @Override
  public Object resolve(ExternalTaskTriggerDTO externalTaskTriggerDTO) {
    Map<String, String> headers = externalTaskTriggerDTO.getHeaders();
    if (type == Map.class) {
      return headers;
    }
    return objectMapper.convertValue(headers, type);
  }
}
