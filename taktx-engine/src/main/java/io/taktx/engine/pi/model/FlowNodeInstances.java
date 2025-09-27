/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks All rights reserved.
 * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 * Free use is permitted with up to 3 Kafka partitions per topic. See LICENSE file for details.
 * For commercial use or more partitions and features, contact [https://www.taktx.io/contact].
 */

package io.taktx.engine.pi.model;

import io.taktx.dto.InstanceScheduleKeyDTO;
import io.taktx.dto.ScopeState;
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
  private Map<String, Set<String>> messageSubscriptions;
  private Set<InstanceScheduleKeyDTO> scheduleKeys;
  private int activeCnt;
  private ScopeState state;
  private boolean stateChanged;
  private FlowNodeInstance<?> parentFlowNodeInstance;
  private long elementInstanceCnt;

  public FlowNodeInstances() {
    this.instances = new LinkedHashMap<>();
    this.messageSubscriptions = new HashMap<>();
    this.gatewayInstances = new HashMap<>();
    this.scheduleKeys = new HashSet<>();
    this.state = ScopeState.ACTIVE;
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

    if ((state == ScopeState.ACTIVE || state == ScopeState.INITIALIZED) && activeCnt == 0) {
      this.setState(ScopeState.COMPLETED);
    }
  }

  public void addMessageSubscription(String messageName, String correlationKey) {
    Set<String> correlationKeys =
        messageSubscriptions.computeIfAbsent(messageName, ignored -> new HashSet<>());
    correlationKeys.add(correlationKey);
  }

  public Optional<FlowNodeInstance<?>> getInstanceWithFlowNode(FlowNode flowNode) {
    return instances.values().stream()
        .filter(flowNodeInstance -> flowNodeInstance.getFlowNode().equals(flowNode))
        .findFirst();
  }

  public void setState(ScopeState state) {
    this.stateChanged = this.state != state;
    this.state = state;
  }

  public void setStateNoChange(ScopeState state) {
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
      if ((instance.wasNew() || instance.wasAwaiting()) && instance.isDone()) {
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

  public void addScheduledKey(InstanceScheduleKeyDTO scheduledKey) {
    this.scheduleKeys.add(scheduledKey);
  }
}
