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

import io.taktx.dto.ProcessInstanceState;
import io.taktx.engine.pd.model.FlowNode;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class FlowNodeInstances {

  private final Map<Long, FlowNodeInstance<?>> instances;
  private int activeCnt;
  private ProcessInstanceState state;
  private boolean stateChanged;
  private FlowNodeInstance<?> parentFlowNodeInstance;
  private long elementInstanceCnt;

  public FlowNodeInstances() {
    this.instances = new LinkedHashMap<>();
    this.state = ProcessInstanceState.START;
    this.stateChanged = false;
    this.activeCnt = 0;
    this.elementInstanceCnt = 0;
  }

  public void putInstance(FlowNodeInstance<?> fLowNodeInstance) {
    instances.put(fLowNodeInstance.getElementInstanceId(), fLowNodeInstance);
  }

  public FlowNodeInstance<?> getInstanceWithInstanceId(long elementInstanceId) {
    return instances.get(elementInstanceId);
  }

  public void determineImplicitCompletedState() {
    updateActiveCountForInstances();

    if (state == ProcessInstanceState.ACTIVE && activeCnt == 0) {
      this.setState(ProcessInstanceState.COMPLETED);
    }
  }

  public Optional<FlowNodeInstance<?>> getInstanceWithFlowNode(FlowNode flowNode) {
    return instances.values().stream()
        .filter(flowNodeInstance -> flowNodeInstance.getFlowNode().equals(flowNode))
        .findFirst();
  }

  public void setState(ProcessInstanceState state) {
    this.stateChanged = this.state != null && this.state != state;
    this.state = state;
  }

  public boolean isStateChanged() {
    return stateChanged
        || instances.values().stream()
            .filter(WithFlowNodeInstances.class::isInstance)
            .map(WithFlowNodeInstances.class::cast)
            .anyMatch(instance -> instance.getFlowNodeInstances().isStateChanged());
  }

  public void updateActiveCountForInstances() {
    for (FlowNodeInstance<?> instance : instances.values()) {
      if (instance.wasNew()) {
        activeCnt++;
      }
      if ((instance.wasNew() || instance.wasAwaiting()) && instance.isCompleted()) {
        activeCnt--;
      }
    }
    if (activeCnt < 0) {
      throw new IllegalStateException("Active count cannot be negative");
    }
  }

  public boolean isDirty() {
    return stateChanged || instances.values().stream().anyMatch(FlowNodeInstance::isDirty);
  }

  public long nextElementInstanceId() {
    return ++elementInstanceCnt;
  }
}
