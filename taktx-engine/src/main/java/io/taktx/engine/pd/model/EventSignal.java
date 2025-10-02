/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks All rights reserved.
 * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 * Free use is permitted with up to 3 Kafka partitions per topic. See LICENSE file for details.
 * For commercial use or more partitions and features, contact [https://www.taktx.io/contact].
 */

package io.taktx.engine.pd.model;

import io.taktx.engine.pi.model.FlowNodeInstance;
import io.taktx.engine.pi.model.WithScope;
import java.util.LinkedList;
import lombok.Getter;

@Getter
public abstract class EventSignal {
  private final LinkedList<FlowNodeInstance<?>> pathToSource = new LinkedList<>();

  protected EventSignal(FlowNodeInstance<?> fLowNodeInstance) {
    pathToSource.addFirst(fLowNodeInstance);
  }

  public void bubbleUp() {
    WithScope parentInstance = pathToSource.getLast().getParentInstance();
    if (parentInstance != null) {
      pathToSource.addFirst((FlowNodeInstance<?>) parentInstance);
    }
  }

  public FlowNodeInstance<?> getCurrentInstance() {
    return pathToSource.getFirst();
  }
}
