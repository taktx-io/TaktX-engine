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
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

@Getter
@Setter
@Slf4j
public class FlowNodeInstances {

  private final Map<Long, FlowNodeInstance<?>> instances;
  private Map<String, Long> gatewayInstances;
  private Map<String, Set<String>> messageSubscriptions = new HashMap<>();
  private int activeCnt;
  private ProcessInstanceState state;
  private boolean stateChanged;
  private FlowNodeInstance<?> parentFlowNodeInstance;
  private long elementInstanceCnt;

  public FlowNodeInstances() {
    this.instances = new LinkedHashMap<>();
    this.messageSubscriptions = new HashMap<>();
    this.gatewayInstances = new HashMap<>();
    this.state = ProcessInstanceState.START;
    this.stateChanged = false;
    this.activeCnt = 0;
    this.elementInstanceCnt = 0;
  }

  public void putInstance(FlowNodeInstance<?> fLowNodeInstance) {
    if (fLowNodeInstance instanceof GatewayInstance<?> gatewayInstance) {
      gatewayInstances.put(
          fLowNodeInstance.getFlowNode().getId(), gatewayInstance.getElementInstanceId());
    }
    instances.put(fLowNodeInstance.getElementInstanceId(), fLowNodeInstance);
  }

  public Long getGatewayInstanceId(String flowNodeId) {
    return gatewayInstances.get(flowNodeId);
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

  public void addMessageSubscription(String messageName, String correlationKey) {
    Set<String> correlationKeys =
        messageSubscriptions.computeIfAbsent(messageName, key -> new HashSet<>());
    correlationKeys.add(correlationKey);
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
    log.info("Updating active count for instances, current active count: {}", activeCnt);
    for (FlowNodeInstance<?> instance : instances.values()) {
      log.info("Processing instance: {}", instance.getFlowNode().getId());
      if (instance.wasNew()) {
        log.info("increasing active count for new instance: {}", instance.getFlowNode().getId());
        activeCnt++;
      }
      if ((instance.wasNew() || instance.wasAwaiting()) && instance.isCompleted()) {
        log.info(
            "decreasing active count for new instance: {} wasNew: {} wasAwaiting: {} isCompleted: {}",
            instance.getFlowNode().getId(),
            instance.wasNew(),
            instance.wasAwaiting(),
            instance.isCompleted());
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
