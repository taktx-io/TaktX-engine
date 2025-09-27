/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks All rights reserved.
 * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 * Free use is permitted with up to 3 Kafka partitions per topic. See LICENSE file for details.
 * For commercial use or more partitions and features, contact [https://www.taktx.io/contact].
 */

package io.taktx.engine.pi.model;

import io.taktx.dto.FlowNodeStateEnum;
import io.taktx.dto.ScopeState;
import io.taktx.engine.pd.model.Activity;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@NoArgsConstructor
@Getter
@Setter
public class MultiInstanceInstance extends ActivityInstance<Activity>
    implements WithFlowNodeInstances {
  private FlowNodeInstances flowNodeInstances = new FlowNodeInstances();

  public MultiInstanceInstance(
      Activity activity, FlowNodeInstance<?> parentInstance, long elementInstanceId) {
    super(parentInstance, activity, elementInstanceId);
    setState(FlowNodeStateEnum.INITIAL);
  }

  @Override
  public void setState(FlowNodeStateEnum state) {
    super.setState(state);
    switch (state) {
      case INITIAL -> flowNodeInstances.setState(ScopeState.INITIALIZED);
      case ACTIVE -> flowNodeInstances.setState(ScopeState.ACTIVE);
      case CANCELED -> flowNodeInstances.setState(ScopeState.CANCELED);
      case ABORTED -> flowNodeInstances.setState(ScopeState.ABORTED);
      case COMPLETED -> flowNodeInstances.setState(ScopeState.COMPLETED);
    }
  }
}
