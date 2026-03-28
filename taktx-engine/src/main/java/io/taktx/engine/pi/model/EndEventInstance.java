/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */

package io.taktx.engine.pi.model;

import io.taktx.engine.pd.model.EndEvent;
import lombok.NoArgsConstructor;

@NoArgsConstructor
public class EndEventInstance extends ThrowEventInstance<EndEvent> {

  public EndEventInstance(
      IFlowNodeInstance parentInstance, EndEvent flowNode, long elementInstanceId) {
    super(parentInstance, flowNode, elementInstanceId);
  }

  @Override
  public boolean stateAllowsStart() {
    return true;
  }

  @Override
  public boolean stateAllowsStopping() {
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
  public boolean isDone() {
    return true;
  }

  @Override
  public void abort() {
    // Do nothing
  }
}
