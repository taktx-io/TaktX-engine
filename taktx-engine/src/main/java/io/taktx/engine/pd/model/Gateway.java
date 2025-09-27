/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks All rights reserved.
 * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 * Free use is permitted with up to 3 Kafka partitions per topic. See LICENSE file for details.
 * For commercial use or more partitions and features, contact [https://www.taktx.io/contact].
 */

package io.taktx.engine.pd.model;

import io.taktx.engine.pi.model.FlowNodeInstance;
import io.taktx.engine.pi.model.GatewayInstance;
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
  public FlowNodeInstance<?> newInstance(FlowNodeInstance<?> parentInstance, Scope scope) {
    return newSpecificGatewayInstance(parentInstance, scope.nextElementInstanceId());
  }

  protected abstract GatewayInstance<?> newSpecificGatewayInstance(
      FlowNodeInstance<?> parentInstance, long elementInstanceId);
}
