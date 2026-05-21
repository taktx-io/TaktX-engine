/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.dto;

import io.taktx.proto.VariableValue;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

@EqualsAndHashCode(callSuper = true)
@Data
@NoArgsConstructor
@ToString(callSuper = true)
public abstract class ActivityInstanceDTO extends FlowNodeInstanceDTO {

  private boolean iteration = false;

  private long nextIterationId;

  private VariableValue inputElement;

  private VariableValue outputElement;

  private int loopCnt;

  public void setInputElement(VariableValue inputElement) {
    this.inputElement = normalize(inputElement);
  }

  public void setOutputElement(VariableValue outputElement) {
    this.outputElement = normalize(outputElement);
  }

  private static VariableValue normalize(VariableValue value) {
    if (value != null) {
      value.getSerializedSize();
    }
    return value;
  }
}
