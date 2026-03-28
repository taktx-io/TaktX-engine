/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */

package io.taktx.engine.pi.model;

import io.taktx.dto.ExecutionState;
import io.taktx.engine.pd.model.FlowElements;
import io.taktx.engine.pd.model.SubProcess;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@NoArgsConstructor
@Setter
@Getter
public class SubProcessInstance extends ActivityInstance<SubProcess> implements WithScope {

  private Scope scope;

  public SubProcessInstance(
      IFlowNodeInstance parentInstance, SubProcess flowNode, long elementInstanceId) {
    super(parentInstance, flowNode, elementInstanceId);
  }

  @Override
  public ExecutionState getState() {
    return scope.getState();
  }

  public FlowElements getFlowElements() {
    return getFlowNode().getElements();
  }
}
