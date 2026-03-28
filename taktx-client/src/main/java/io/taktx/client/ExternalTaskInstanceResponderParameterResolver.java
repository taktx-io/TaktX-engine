/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.client;

import io.taktx.dto.ExternalTaskTriggerDTO;

/**
 * A parameter resolver that provides an ExternalTaskInstanceResponder for handling external task
 * instances.
 */
public class ExternalTaskInstanceResponderParameterResolver implements ParameterResolver {

  private final ProcessInstanceResponder externalTaskResponder;

  /**
   * Constructor for ExternalTaskInstanceResponderParameterResolver.
   *
   * @param externalTaskResponder The responder to handle external task instances.
   */
  public ExternalTaskInstanceResponderParameterResolver(
      ProcessInstanceResponder externalTaskResponder) {
    this.externalTaskResponder = externalTaskResponder;
  }

  @Override
  public Object resolve(ExternalTaskTriggerDTO externalTaskTriggerDTO) {
    return externalTaskResponder.responderForExternalTaskTrigger(externalTaskTriggerDTO);
  }
}
