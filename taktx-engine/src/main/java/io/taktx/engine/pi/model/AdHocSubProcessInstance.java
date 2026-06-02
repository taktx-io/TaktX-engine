/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.engine.pi.model;

import io.taktx.engine.pd.model.AdHocSubProcess;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@NoArgsConstructor
@Getter
@Setter
public class AdHocSubProcessInstance extends SubProcessInstance {

  /** True once completionCondition has evaluated to true when cancelRemainingInstances=false. */
  private boolean completionConditionTriggered;

  public AdHocSubProcessInstance(
      IFlowNodeInstance parentInstance, AdHocSubProcess flowNode, long elementInstanceId) {
    super(parentInstance, flowNode, elementInstanceId);
  }

  @Override
  public AdHocSubProcess getFlowNode() {
    return (AdHocSubProcess) super.getFlowNode();
  }
}
