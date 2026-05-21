/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */

package io.taktx.engine.pd.model;

import io.taktx.engine.pi.model.IFlowNodeInstance;
import io.taktx.proto.VariableValue;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public abstract class EventSignal {
  private final LinkedList<IFlowNodeInstance> pathToSource = new LinkedList<>();
  private Map<String, VariableValue> variables = new HashMap<>();

  protected EventSignal(IFlowNodeInstance fLowNodeInstance, Map<String, VariableValue> variables) {
    this.variables = variables != null ? new HashMap<>(variables) : new HashMap<>();
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
