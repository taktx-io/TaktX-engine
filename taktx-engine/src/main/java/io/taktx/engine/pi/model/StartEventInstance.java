/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks All rights reserved.
 * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 * Free use is permitted with up to 3 Kafka partitions per topic. See LICENSE file for details.
 * For commercial use or more partitions and features, contact [https://www.taktx.io/contact].
 */

package io.taktx.engine.pi.model;

import io.taktx.engine.pd.model.StartEvent;
import lombok.NoArgsConstructor;

@NoArgsConstructor
public class StartEventInstance extends CatchEventInstance<StartEvent> {

  public StartEventInstance(
      IFlowNodeInstance parentInstance, StartEvent flowNode, long elementInstanceId) {
    super(parentInstance, flowNode, elementInstanceId);
  }

  @Override
  public boolean stateAllowsStart() {
    return true;
  }

  @Override
  public boolean stateAllowsContinue() {
    return false;
  }

  @Override
  public boolean stateAllowsStopping() {
    return true;
  }

  @Override
  public boolean isDone() {
    return true;
  }

  @Override
  public void abort() {
    // Do nothing
  }
}
