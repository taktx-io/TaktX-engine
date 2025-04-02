/*
 *
 *  * TaktX - A high-performance BPMN engine
 *  * Copyright (c) 2025 TaktX B.V. All rights reserved.
 *  * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 *  * Free use is permitted with up to 3 Kafka partitions. See LICENSE file for details.
 *  * For commercial use or more partitions and features, contact [info@taktx.io] or [https://www.taktx.io/contact].
 *
 */

package com.flomaestro.engine.pd.model;

import com.flomaestro.engine.pi.model.FlowNodeInstance;
import com.flomaestro.engine.pi.model.FlowNodeInstances;
import com.flomaestro.engine.pi.model.GatewayInstance;
import java.util.Optional;
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
  public FlowNodeInstance<?> newInstance(
      FlowNodeInstance<?> parentInstance, FlowNodeInstances flowNodeInstances) {
    Optional<GatewayInstance> optGatewayInstance =
        flowNodeInstances.getInstances().values().stream()
            .filter(GatewayInstance.class::isInstance)
            .map(GatewayInstance.class::cast)
            .filter(instance -> instance.getFlowNode().getId().equals(getId()))
            .findFirst();
    return optGatewayInstance.orElse(
        newSpecificGatewayInstance(parentInstance, flowNodeInstances.nextElementInstanceId()));
  }

  protected abstract GatewayInstance<?> newSpecificGatewayInstance(
      FlowNodeInstance<?> parentInstance, long elementInstanceId);
}
