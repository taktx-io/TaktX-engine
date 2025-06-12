/*
 *
 *  * TaktX - A high-performance BPMN engine
 *  * Copyright (c) 2025 TaktX B.V. All rights reserved.
 *  * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 *  * Free use is permitted with up to 3 Kafka partitions. See LICENSE file for details.
 *  * For commercial use or more partitions and features, contact [info@taktx.io] or [https://www.taktx.io/contact].
 *
 */

package io.taktx.engine.pi.model;

import io.taktx.engine.pd.model.InclusiveGateway;
import java.util.HashSet;
import java.util.Set;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class InclusiveGatewayInstance extends GatewayInstance<InclusiveGateway> {
  private Set<String> triggeredInputFlows = new HashSet<>();

  public InclusiveGatewayInstance(
      FlowNodeInstance<?> parentInstance, InclusiveGateway flowNode, long elementInstanceId) {
    super(parentInstance, flowNode, elementInstanceId);
  }

  @Override
  public void resetFlows() {
    this.triggeredInputFlows.clear();
  }

  public void addTriggeredInputFlow(String inputFlowId) {
    this.triggeredInputFlows.add(inputFlowId);
  }
}
