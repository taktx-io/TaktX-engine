/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */

package io.taktx.engine.pi.model;

import com.fasterxml.jackson.databind.JsonNode;
import io.taktx.dto.ExecutionState;
import io.taktx.engine.pd.model.Activity;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@NoArgsConstructor
@Setter
@Getter
public abstract class ActivityInstance<N extends Activity> extends FlowNodeInstance<N> {
  private boolean iteration = false;
  private long nextIterationId;
  private JsonNode inputElement;
  private JsonNode outputElement;
  private int loopCnt;

  protected ActivityInstance(IFlowNodeInstance parentInstance, N flowNode, long elementInstanceId) {
    super(parentInstance, flowNode, elementInstanceId);
  }

  public void setOutputElement(JsonNode outputElement) {
    this.outputElement = outputElement;
    setDirty();
  }

  public void setInputElement(JsonNode inputElement) {
    this.inputElement = inputElement;
    setDirty();
  }

  @Override
  public void abort() {
    setState(ExecutionState.ABORTED);
  }

  @Override
  public boolean canSelectNextNodeStart() {
    return isDone();
  }

  @Override
  public boolean canSelectNextNodeContinue() {
    return getState() == ExecutionState.COMPLETED;
  }

  @Override
  public boolean isIteration() {
    return iteration;
  }
}
