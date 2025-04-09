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

import io.taktx.dto.ActtivityStateEnum;
import io.taktx.dto.ProcessInstanceState;
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
    setState(ActtivityStateEnum.INITIAL);
  }

  @Override
  public void setState(ActtivityStateEnum state) {
    super.setState(state);
    switch (state) {
      case INITIAL -> flowNodeInstances.setState(ProcessInstanceState.START);
      case STARTED -> flowNodeInstances.setState(ProcessInstanceState.ACTIVE);
      case WAITING -> flowNodeInstances.setState(ProcessInstanceState.ACTIVE);
      case TERMINATED -> flowNodeInstances.setState(ProcessInstanceState.TERMINATED);
      case FAILED -> flowNodeInstances.setState(ProcessInstanceState.FAILED);
      case FINISHED -> flowNodeInstances.setState(ProcessInstanceState.COMPLETED);
    }
  }
}
