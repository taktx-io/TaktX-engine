/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */

package io.taktx.engine.pi.model;

import io.taktx.engine.pd.model.EventBasedGateway;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class EventBasedGatewayInstance extends GatewayInstance<EventBasedGateway> {
  private List<Long> connectedFlowNodeInstanceIds;

  public EventBasedGatewayInstance(
      IFlowNodeInstance parentInstance, EventBasedGateway flowNode, long elementInstanceId) {
    super(parentInstance, flowNode, elementInstanceId);
  }

  @Override
  public void resetFlows() {
    // No specific flows to reset for EventBasedGatewayInstance
  }
}
