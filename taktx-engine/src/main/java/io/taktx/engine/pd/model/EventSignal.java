/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks All rights reserved.
 * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 * Free use is permitted with up to 3 Kafka partitions per topic. See LICENSE file for details.
 * For commercial use or more partitions and features, contact [https://www.taktx.io/contact].
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
