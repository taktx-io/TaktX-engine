/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks All rights reserved.
 * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 * Free use is permitted with up to 3 Kafka partitions per topic. See LICENSE file for details.
 * For commercial use or more partitions and features, contact [https://www.taktx.io/contact].
 */

package io.taktx.engine.pi.model;

import io.taktx.dto.ExecutionState;
import io.taktx.engine.pd.model.Activity;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@NoArgsConstructor
@Getter
@Setter
public class MultiInstanceInstance extends ActivityInstance<Activity> implements WithScope {
  private Scope scope;

  public MultiInstanceInstance(
      Activity activity, IFlowNodeInstance parentInstance, long elementInstanceId) {
    super(parentInstance, activity, elementInstanceId);
  }

  @Override
  public ExecutionState getState() {
    return scope.getState();
  }
}
