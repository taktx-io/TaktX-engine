/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */

package io.taktx.engine.pi.model;

import io.taktx.dto.VariablesDTO;
import io.taktx.engine.pd.model.EventSignal;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class ErrorEventSignal extends EventSignal {
  private String code;
  private String message;

  public ErrorEventSignal(
      FlowNodeInstance<?> fLowNodeInstance, String code, String message, VariablesDTO variables) {
    super(fLowNodeInstance, variables);
    this.code = code;
    this.message = message;
  }

  @Override
  public boolean shouldBubbleUp() {
    return true;
  }
}
