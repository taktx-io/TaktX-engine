/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks All rights reserved.
 * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 * Free use is permitted with up to 3 Kafka partitions per topic. See LICENSE file for details.
 * For commercial use or more partitions and features, contact [https://www.taktx.io/contact].
 */

package io.taktx.engine.pi.model;

import io.taktx.dto.ExecutionState;
import io.taktx.dto.InstanceScheduleKeyDTO;
import io.taktx.engine.pd.model.FlowElements;
import io.taktx.engine.pi.DirectInstanceResult;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

@Getter
@Setter
@Slf4j
@NoArgsConstructor
public class Scope {
  private Scope parentScope;
  private int subProcessLevel;
  private UUID processInstanceId;
  private FlowElements flowElements;
  private VariableScope variableScope;
  private FlowNodeInstanceScope flowNodeInstanceScope;
  private Map<String, Long> gatewayInstances;
  private Map<String, Set<String>> messageSubscriptions;
  private Set<InstanceScheduleKeyDTO> scheduleKeys;
  private int activeCnt;
  private DirectInstanceResult directInstanceResult;
  private ExecutionState initialState;
  private ExecutionState state;
  private boolean stateChanged;
  private WithScope parentFlowNodeInstance;
  private long elementInstanceCnt;

  public Scope(
      Scope parentScope,
      UUID processInstanceId,
      WithScope parentFlowNodeInstance,
      FlowElements flowElements,
      VariableScope variableScope,
      FlowNodeInstanceScope flowNodeInstanceScope) {
    this.parentScope = parentScope;
    this.subProcessLevel = parentScope != null ? parentScope.getSubProcessLevel() + 1 : 0;
    this.processInstanceId = processInstanceId;
    this.parentFlowNodeInstance = parentFlowNodeInstance;
    this.flowElements = flowElements;
    this.variableScope = variableScope;
    this.flowNodeInstanceScope = flowNodeInstanceScope;
    this.messageSubscriptions = new HashMap<>();
    this.gatewayInstances = new HashMap<>();
    this.scheduleKeys = new HashSet<>();
    this.state = ExecutionState.INITIALIZED;
    this.stateChanged = false;
    this.activeCnt = 0;
    this.elementInstanceCnt = 0;
    this.directInstanceResult = DirectInstanceResult.empty();
  }

  public void putInstance(FlowNodeInstance<?> fLowNodeInstance) {
    if (fLowNodeInstance instanceof GatewayInstance<?> gatewayInstance) {
      gatewayInstances.put(
          fLowNodeInstance.getFlowNode().getId(), gatewayInstance.getElementInstanceId());
    }
    flowNodeInstanceScope.putInstance(fLowNodeInstance);
  }

  public Long getGatewayInstanceId(String flowNodeId) {
    return gatewayInstances.get(flowNodeId);
  }

  public void addMessageSubscription(String messageName, String correlationKey) {
    Set<String> correlationKeys =
        messageSubscriptions.computeIfAbsent(messageName, ignored -> new HashSet<>());
    correlationKeys.add(correlationKey);
  }

  public void setState(ExecutionState state) {
    this.stateChanged = this.state != state;
    this.state = state;
  }

  public void setActiveCnt(int activeCnt) {
    this.activeCnt = activeCnt;
    this.initialState = getState();
  }

  public void setStateNoChange(ExecutionState state) {
    this.state = state;
    this.initialState = getState();
  }

  public boolean isStateChanged() {
    return stateChanged
        || initialState != getState()
        || flowNodeInstanceScope.getInstances().values().stream()
            .filter(WithScope.class::isInstance)
            .map(WithScope.class::cast)
            .anyMatch(instance -> instance.getScope().isStateChanged());
  }

  public void updateActiveCountForInstances() {
    for (FlowNodeInstance<?> instance : flowNodeInstanceScope.getInstances().values()) {
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

  public ExecutionState getState() {
    if (state == ExecutionState.INITIALIZED) {
      if (activeCnt == 0) {
        return ExecutionState.COMPLETED;
      } else if (activeCnt > 0) {
        return ExecutionState.ACTIVE;
      } else {
        throw new IllegalStateException("Active count cannot be negative");
      }
    } else {
      return state;
    }
  }

  public boolean isDirty() {
    return stateChanged
        || flowNodeInstanceScope.getInstances().values().stream()
            .anyMatch(FlowNodeInstance::isDirty);
  }

  public long nextElementInstanceId() {
    return ++elementInstanceCnt;
  }

  public void addScheduledKey(InstanceScheduleKeyDTO scheduledKey) {
    this.scheduleKeys.add(scheduledKey);
  }

  public Scope selectChildScope(WithScope withScope) {
    return new Scope(
        this,
        processInstanceId,
        withScope,
        withScope.getFlowElements(),
        variableScope.selectChildScope(withScope.getElementInstanceId()),
        flowNodeInstanceScope.selectChildScope(withScope));
  }
}
