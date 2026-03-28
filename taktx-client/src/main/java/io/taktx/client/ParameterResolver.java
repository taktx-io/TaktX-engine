/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.client;

import io.taktx.dto.ExternalTaskTriggerDTO;

/** A resolver interface for resolving method parameters from ExternalTaskTriggerDTOs. */
public interface ParameterResolver {

  /**
   * Resolves a method parameter from the given ExternalTaskTriggerDTO.
   *
   * @param externalTaskTriggerDTO The DTO containing data for resolution.
   * @return The resolved parameter value.
   */
  Object resolve(ExternalTaskTriggerDTO externalTaskTriggerDTO);
}
