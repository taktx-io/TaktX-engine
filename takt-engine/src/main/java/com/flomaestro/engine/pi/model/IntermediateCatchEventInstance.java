/*
 *
 *  * TaktX - A high-performance BPMN engine
 *  * Copyright (c) 2025 TaktX B.V. All rights reserved.
 *  * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 *  * Free use is permitted with up to 3 Kafka partitions. See LICENSE file for details.
 *  * For commercial use or more partitions and features, contact [info@taktx.io] or [https://www.taktx.io/contact].
 *
 */

package com.flomaestro.engine.pi.model;

import com.flomaestro.engine.pd.model.IntermediateCatchEvent;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@NoArgsConstructor
@Getter
@Setter
public class IntermediateCatchEventInstance extends CatchEventInstance<IntermediateCatchEvent> {

  public IntermediateCatchEventInstance(
      FlowNodeInstance<?> parentInstance, IntermediateCatchEvent flowNode, long elementInstanceId) {
    super(parentInstance, flowNode, elementInstanceId);
  }
}
