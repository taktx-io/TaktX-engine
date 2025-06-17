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

import io.taktx.engine.pd.model.ScriptTask;
import lombok.NoArgsConstructor;

@NoArgsConstructor
public class ScriptTaskInstance extends ExternalTaskInstance<ScriptTask> {

  public ScriptTaskInstance(
      FlowNodeInstance<?> parentInstance, ScriptTask flowNode, long elementInstanceId) {
    super(parentInstance, flowNode, elementInstanceId);
  }
}
