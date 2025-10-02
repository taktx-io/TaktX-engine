/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks All rights reserved.
 * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 * Free use is permitted with up to 3 Kafka partitions per topic. See LICENSE file for details.
 * For commercial use or more partitions and features, contact [https://www.taktx.io/contact].
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

  protected ActivityInstance(WithScope parentInstance, N flowNode, long elementInstanceId) {
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
}
