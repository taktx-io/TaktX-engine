/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks All rights reserved.
 * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 * Free use is permitted with up to 3 Kafka partitions per topic. See LICENSE file for details.
 * For commercial use or more partitions and features, contact [https://www.taktx.io/contact].
 */

package io.taktx.engine.pi.model;

import io.taktx.engine.pd.model.BoundaryEvent;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@NoArgsConstructor
@Getter
@Setter
public class BoundaryEventInstance extends CatchEventInstance<BoundaryEvent> {

  public BoundaryEventInstance(
      WithScope parentInstance, BoundaryEvent flowNode, long elementInstanceId) {
    super(parentInstance, flowNode, elementInstanceId);
  }

  @Override
  public boolean canSelectNextNodeStart() {
    return true;
  }

  @Override
  public boolean canSelectNextNodeContinue() {
    return isDone() || !getFlowNode().isCancelActivity();
  }
}
