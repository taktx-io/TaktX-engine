/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks All rights reserved.
 * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 * Free use is permitted with up to 3 Kafka partitions per topic. See LICENSE file for details.
 * For commercial use or more partitions and features, contact [https://www.taktx.io/contact].
 */

package io.taktx.engine.pd.model;

import io.taktx.engine.pi.model.FlowNodeInstance;
import io.taktx.engine.pi.model.FlowNodeInstances;
import java.util.HashSet;
import java.util.Set;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Getter
@SuperBuilder
@NoArgsConstructor
public abstract class FlowNode extends FlowElement {
  @Builder.Default private Set<String> incoming = new HashSet<>();
  @Builder.Default private Set<String> outgoing = new HashSet<>();

  private final Set<SequenceFlow> incomingSequenceFlows = new HashSet<>();
  private final Set<SequenceFlow> outGoingSequenceFlows = new HashSet<>();

  public FlowNodeInstance<?> createAndStoreNewInstance(
      FlowNodeInstance<?> parentInstance, FlowNodeInstances flowNodeInstances) {
    FlowNodeInstance<?> newInstance = newInstance(parentInstance, flowNodeInstances);
    newInstance.setInitialState();
    flowNodeInstances.putInstance(newInstance);
    return newInstance;
  }

  public abstract FlowNodeInstance<?> newInstance(
      FlowNodeInstance<?> parentInstance, FlowNodeInstances flowNodeInstances);
}
