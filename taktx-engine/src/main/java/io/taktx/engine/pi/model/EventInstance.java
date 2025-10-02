/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks All rights reserved.
 * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 * Free use is permitted with up to 3 Kafka partitions per topic. See LICENSE file for details.
 * For commercial use or more partitions and features, contact [https://www.taktx.io/contact].
 */

package io.taktx.engine.pi.model;

import io.taktx.engine.pd.model.Event;
import lombok.NoArgsConstructor;

@NoArgsConstructor
public abstract class EventInstance<N extends Event> extends FlowNodeInstance<N> {

  protected EventInstance(WithScope parentInstance, N flowNode, long elementInstanceId) {
    super(parentInstance, flowNode, elementInstanceId);
  }

  @Override
  public boolean canSelectNextNodeStart() {
    return isDone();
  }

  @Override
  public boolean stateChanged() {
    return false;
  }

  @Override
  public boolean canSelectNextNodeContinue() {
    return isDone();
  }
}
