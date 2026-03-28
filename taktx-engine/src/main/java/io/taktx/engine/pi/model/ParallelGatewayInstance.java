/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */

package io.taktx.engine.pi.model;

import io.taktx.engine.pd.model.ParallelGateway;
import java.util.HashSet;
import java.util.Set;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class ParallelGatewayInstance extends GatewayInstance<ParallelGateway> {
  private final Set<String> triggeredFlows = new HashSet<>();

  public ParallelGatewayInstance(
      IFlowNodeInstance parentInstance, ParallelGateway flowNode, long elementInstanceId) {
    super(parentInstance, flowNode, elementInstanceId);
  }

  @Override
  public void resetFlows() {
    this.triggeredFlows.clear();
  }

  public void addTriggeredFlow(String inputFlowId) {
    this.triggeredFlows.add(inputFlowId);
  }
}
