/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */

package io.taktx.engine.pd.model;

import io.taktx.dto.VariablesDTO;
import io.taktx.engine.pi.model.IFlowNodeInstance;
import java.util.LinkedList;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public abstract class EventSignal {
  private final LinkedList<IFlowNodeInstance> pathToSource = new LinkedList<>();
  private VariablesDTO variables = VariablesDTO.empty();

  protected EventSignal(IFlowNodeInstance fLowNodeInstance, VariablesDTO variables) {
    this.variables = variables;
    pathToSource.addFirst(fLowNodeInstance);
  }

  public void bubbleUp() {
    IFlowNodeInstance parentInstance = pathToSource.getLast().getParentInstance();
    if (parentInstance != null) {
      pathToSource.addFirst(parentInstance);
    }
  }

  public IFlowNodeInstance getCurrentInstance() {
    return !pathToSource.isEmpty() ? pathToSource.getFirst() : null;
  }

  public boolean shouldBubbleUp() {
    return false;
  }
}
