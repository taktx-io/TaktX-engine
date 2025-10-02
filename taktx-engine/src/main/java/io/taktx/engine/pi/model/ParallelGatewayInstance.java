/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks All rights reserved.
 * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 * Free use is permitted with up to 3 Kafka partitions per topic. See LICENSE file for details.
 * For commercial use or more partitions and features, contact [https://www.taktx.io/contact].
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
      WithScope parentInstance, ParallelGateway flowNode, long elementInstanceId) {
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
