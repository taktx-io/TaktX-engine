/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks All rights reserved.
 * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 * Free use is permitted with up to 3 Kafka partitions per topic. See LICENSE file for details.
 * For commercial use or more partitions and features, contact [https://www.taktx.io/contact].
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
      WithScope parentInstance, EventBasedGateway flowNode, long elementInstanceId) {
    super(parentInstance, flowNode, elementInstanceId);
  }

  @Override
  public void resetFlows() {
    // No specific flows to reset for EventBasedGatewayInstance
  }
}
