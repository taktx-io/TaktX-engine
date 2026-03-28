/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */

package io.taktx.engine.pi;

import io.taktx.engine.pi.model.FlowNodeInstance;
import lombok.Getter;

@Getter
public class ProcessInstanceException extends RuntimeException {

  private final transient FlowNodeInstance<?> flowNodeInstance;

  public ProcessInstanceException(FlowNodeInstance<?> flowNodeInstance, String message) {
    super(message);
    this.flowNodeInstance = flowNodeInstance;
  }
}
