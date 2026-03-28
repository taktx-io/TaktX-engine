/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.client;

import io.taktx.dto.ExternalTaskTriggerDTO;

/** A parameter resolver that directly provides the ExternalTaskTriggerDTO. */
public class ExternalTaskTriggerDTOParameterResolver implements ParameterResolver {

  /**
   * Constructs a new ExternalTaskTriggerDTOParameterResolver. This explicit no-argument constructor
   * is documented to satisfy static analysis rules that require constructors to have comments
   * instead of relying on the implicit default constructor.
   */
  public ExternalTaskTriggerDTOParameterResolver() {
    // intentionally empty
  }

  @Override
  public Object resolve(ExternalTaskTriggerDTO externalTaskTriggerDTO) {
    return externalTaskTriggerDTO;
  }
}
