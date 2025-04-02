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
import com.flomaestro.engine.pi.model.IntermediateCatchEventInstance;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Getter
@SuperBuilder
@NoArgsConstructor
public class IntermediateCatchEvent extends CatchEvent {

  @Override
  public FlowNodeInstance<?> newInstance(
      FlowNodeInstance<?> parentInstance, FlowNodeInstances flowNodeInstances) {
    return new IntermediateCatchEventInstance(
        parentInstance, this, flowNodeInstances.nextElementInstanceId());
  }

  public boolean hasLinkEventDefinition(String name) {
    return getLinkventDefinition().stream().anyMatch(e -> e.getName().equals(name));
  }
}
