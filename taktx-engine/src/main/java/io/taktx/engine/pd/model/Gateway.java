/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */

package io.taktx.engine.pd.model;

import io.taktx.engine.pi.model.FlowNodeInstance;
import io.taktx.engine.pi.model.GatewayInstance;
import io.taktx.engine.pi.model.IFlowNodeInstance;
import io.taktx.engine.pi.model.Scope;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@Getter
@SuperBuilder
@NoArgsConstructor
public abstract class Gateway extends FlowNode {
  private String defaultFlow;
  @Setter private SequenceFlow defaultSequenceFlow;

  @Override
  public FlowNodeInstance<?> newInstance(IFlowNodeInstance parentInstance, Scope scope) {
    return newSpecificGatewayInstance(parentInstance, scope.nextElementInstanceId());
  }

  protected abstract GatewayInstance<?> newSpecificGatewayInstance(
      IFlowNodeInstance parentInstance, long elementInstanceId);
}
