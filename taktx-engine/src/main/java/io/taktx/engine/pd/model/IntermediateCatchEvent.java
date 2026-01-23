/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks All rights reserved.
 * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 * Free use is permitted with up to 3 Kafka partitions per topic. See LICENSE file for details.
 * For commercial use or more partitions and features, contact [https://www.taktx.io/contact].
 */

package io.taktx.engine.pd.model;

import io.taktx.engine.pi.model.FlowNodeInstance;
import io.taktx.engine.pi.model.IFlowNodeInstance;
import io.taktx.engine.pi.model.IntermediateCatchEventInstance;
import io.taktx.engine.pi.model.Scope;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Getter
@SuperBuilder
@NoArgsConstructor
public class IntermediateCatchEvent extends CatchEvent {

  @Override
  public FlowNodeInstance<?> newInstance(IFlowNodeInstance parentInstance, Scope scope) {
    return new IntermediateCatchEventInstance(parentInstance, this, scope.nextElementInstanceId());
  }

  public boolean hasLinkEventDefinition(String name) {
    return getLinkventDefinition().stream().anyMatch(e -> e.getName().equals(name));
  }
}
