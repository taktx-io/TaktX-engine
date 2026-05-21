/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.client;

import io.taktx.client.annotation.CustomHeaders;
import io.taktx.client.annotation.Variable;
import io.taktx.dto.ExternalTaskTriggerDTO;
import io.taktx.dto.VariablesDTO;
import java.lang.reflect.Parameter;
import java.util.Map;

/**
 * Default implementation of TaktParameterResolverFactory that creates parameter resolvers based on
 * parameter types and annotations.
 */
public class DefaultParameterResolverFactory implements ParameterResolverFactory {

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
    Class<?> parameterType = parameter.getType();
    if (ExternalTaskTriggerDTO.class.isAssignableFrom(parameterType)) {
      return new ExternalTaskTriggerDTOParameterResolver();
    } else if (ExternalTaskInstanceResponder.class.isAssignableFrom(parameterType)) {
      return new ExternalTaskInstanceResponderParameterResolver(externalTaskResponder);
    } else if (parameter.getAnnotation(Variable.class) != null) {
      Variable variableAnnotation = parameter.getAnnotation(Variable.class);
      String name =
          !variableAnnotation.value().isEmpty() ? variableAnnotation.value() : parameter.getName();
      return new VariableParameterResolver(parameter.getType(), name);
    } else if (parameter.getAnnotation(CustomHeaders.class) != null) {
      return new HeadersParameterResolver(parameter.getType());
    } else if (Map.class.isAssignableFrom(parameterType)) {
      return new MapParameterResolver();
    } else if (VariablesDTO.class.isAssignableFrom(parameterType)) {
      return new VariablesObjectParameterResolver(parameterType);
    } else if (ClientValueMapper.isSimpleValue(parameterType)
        || Iterable.class.isAssignableFrom(parameterType)) {
      return new VariableParameterResolver(parameterType, parameter.getName());
    } else {
      return new VariablesObjectParameterResolver(parameterType);
    }
  }
}
