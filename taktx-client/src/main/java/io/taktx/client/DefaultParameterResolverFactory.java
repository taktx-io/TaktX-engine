/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks All rights reserved.
 * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 * Free use is permitted with up to 3 Kafka partitions per topic. See LICENSE file for details.
 * For commercial use or more partitions and features, contact [https://www.taktx.io/contact].
 */

package io.taktx.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.cbor.CBORFactory;
import io.taktx.client.annotation.CustomHeaders;
import io.taktx.client.annotation.Variable;
import io.taktx.dto.ExternalTaskTriggerDTO;
import java.lang.reflect.Parameter;
import java.util.Map;

/**
 * Default implementation of TaktParameterResolverFactory that creates parameter resolvers based on
 * parameter types and annotations.
 */
public class DefaultParameterResolverFactory implements ParameterResolverFactory {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper(new CBORFactory());

  private final ProcessInstanceResponder externalTaskResponder;

  /**
   * Constructor for DefaultTaktParameterResolverFactory.
   *
   * @param externalTaskResponder The responder to handle external task instances.
   */
  public DefaultParameterResolverFactory(ProcessInstanceResponder externalTaskResponder) {
    this.externalTaskResponder = externalTaskResponder;
  }

  @Override
  public ParameterResolver create(Parameter parameter) {
    if (parameter.getType().isAssignableFrom(ExternalTaskTriggerDTO.class)) {
      return new ExternalTaskTriggerDTOParameterResolver();
    } else if (parameter.getType().isAssignableFrom(ExternalTaskInstanceResponder.class)) {
      return new ExternalTaskInstanceResponderParameterResolver(externalTaskResponder);
    } else if (parameter.getAnnotation(Variable.class) != null) {
      Variable variableAnnotation = parameter.getAnnotation(Variable.class);
      String name =
          !variableAnnotation.value().isEmpty() ? variableAnnotation.value() : parameter.getName();
      return new VariableParameterResolver(OBJECT_MAPPER, parameter.getType(), name);
    } else if (parameter.getAnnotation(CustomHeaders.class) != null) {
      return new HeadersParameterResolver(OBJECT_MAPPER, parameter.getType());
    } else if (parameter.getType().isAssignableFrom(Map.class)) {
      return new MapParameterResolver(OBJECT_MAPPER);
    } else {
      return new VariableParameterResolver(OBJECT_MAPPER, parameter.getType(), parameter.getName());
    }
  }
}
