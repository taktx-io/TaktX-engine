/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.client;

import io.taktx.dto.ExternalTaskTriggerDTO;
import io.taktx.dto.VariablesDTO;

/** Maps the full variable scope of an external task to a target Java object. */
public class VariablesObjectParameterResolver implements ParameterResolver {

  private final Class<?> type;

  public VariablesObjectParameterResolver(Class<?> type) {
    this.type = type;
  }

  @Override
  public Object resolve(ExternalTaskTriggerDTO externalTaskTriggerDTO) {
    VariablesDTO variables =
        externalTaskTriggerDTO == null
            ? VariablesDTO.empty()
            : externalTaskTriggerDTO.getVariables();
    return ClientValueMapper.fromVariablesDto(variables, type);
  }
}
