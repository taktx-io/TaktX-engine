/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.client;

import io.taktx.dto.ExternalTaskTriggerDTO;

/** A parameter resolver that converts the variables of an ExternalTaskTriggerDTO into a Map. */
public class MapParameterResolver implements ParameterResolver {

  public MapParameterResolver() {}

  /** Backward-compatible constructor retained for callers still passing a mapper instance. */
  public MapParameterResolver(Object ignoredMapper) {
    // Intentionally ignored: mapping is now handled internally.
  }

  @Override
  public Object resolve(ExternalTaskTriggerDTO externalTaskTriggerDTO) {
    return ClientValueMapper.toPlainJavaMap(externalTaskTriggerDTO.getVariables());
  }
}
