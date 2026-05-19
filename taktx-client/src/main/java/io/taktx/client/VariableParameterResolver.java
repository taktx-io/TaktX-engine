/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.client;

import io.taktx.dto.ExternalTaskTriggerDTO;
import io.taktx.proto.VariableValue;
import io.taktx.variables.VariableValueDtoMapper;

/**
 * A parameter resolver that extracts a variable from the ExternalTaskTriggerDTO's variables map and
 * converts it to the specified type using proto-backed variable values.
 */
public class VariableParameterResolver implements ParameterResolver {

  private final Class<?> type;
  private final String name;

  /**
   * Constructs a VariableParameterResolver.
   *
   * @param type the target type to convert the variable to
   * @param name the name of the variable to extract
   */
  public VariableParameterResolver(Class<?> type, String name) {
    this.type = type;
    this.name = name;
  }

  /**
   * Backward-compatible constructor retained for callers still passing a mapper instance.
   *
   * @param ignoredMapper legacy mapper argument that is ignored
   * @param type the target type to convert the variable to
   * @param name the name of the variable to extract
   */
  public VariableParameterResolver(Object ignoredMapper, Class<?> type, String name) {
    this(type, name);
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
    VariableValue value =
        VariableValueDtoMapper.toVariableMap(externalTaskTriggerDTO.getVariables()).get(name);
    if (value != null) {
      return ClientValueMapper.fromVariableValue(value, type);
    }
    return null;
  }
}
