/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks All rights reserved.
 * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 * Free use is permitted with up to 3 Kafka partitions per topic. See LICENSE file for details.
 * For commercial use or more partitions and features, contact [https://www.taktx.io/contact].
 */

package io.taktx.engine.pi.model;

import io.taktx.engine.pd.model.EndEvent;
import lombok.NoArgsConstructor;

@NoArgsConstructor
public class EndEventInstance extends ThrowEventInstance<EndEvent> {

  public EndEventInstance(
      FlowNodeInstance<?> parentInstance, EndEvent flowNode, long elementInstanceId) {
    super(parentInstance, flowNode, elementInstanceId);
  }

  @Override
  public boolean stateAllowsStart() {
    return true;
  }

  @Override
  public boolean stateAllowsTerminate() {
    return false;
  }

  @Override
  public boolean stateAllowsContinue() {
    return false;
  }

  @Override
  public boolean isNotAwaiting() {
    return true;
  }

  @Override
  public boolean isCompleted() {
    return true;
  }

  @Override
  public void terminate() {
    // Do nothing
  }
}
