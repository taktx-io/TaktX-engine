/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks All rights reserved.
 * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 * Free use is permitted with up to 3 Kafka partitions per topic. See LICENSE file for details.
 * For commercial use or more partitions and features, contact [https://www.taktx.io/contact].
 */

package io.taktx.engine.pi.model;

import com.fasterxml.jackson.databind.JsonNode;
import io.taktx.dto.ExecutionState;
import io.taktx.dto.FlowNodeInstanceDTO;
import io.taktx.dto.FlowNodeInstanceKeyDTO;
import io.taktx.dto.InstanceScheduleKeyDTO;
import io.taktx.dto.VariableKeyDTO;
import io.taktx.engine.pd.model.FlowElements;
import io.taktx.engine.pi.DirectInstanceResult;
import io.taktx.engine.pi.ProcessInstanceMapper;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.streams.state.KeyValueStore;

@Getter
@Setter
@Slf4j
@NoArgsConstructor
public class Scope {
  private Scope parentScope;
  private int subProcessLevel;
  private UUID processInstanceId;
  private VariableScope variableScope;
  private FlowNodeInstances flowNodeInstances;
  private Map<String, Long> gatewayInstances = new HashMap<>();
  private Map<String, Set<String>> messageSubscriptions = new HashMap<>();
  private Set<InstanceScheduleKeyDTO> scheduleKeys = new HashSet<>();
  private int activeCnt = 0;
  private DirectInstanceResult directInstanceResult = DirectInstanceResult.empty();
  private ExecutionState initialState;
  private ExecutionState state = ExecutionState.INITIALIZED;
  private boolean stateChanged = false;
  private WithScope parentFlowNodeInstance;
  private long elementInstanceCnt = 0;

  private final Map<Long, Set<Long>> activityToBoundaryEvents = new java.util.WeakHashMap<>();
  private final Map<Long, Long> boundaryEventToActivity = new java.util.WeakHashMap<>();


    public Scope(
      Scope parentScope,
      UUID processInstanceId,
      WithScope parentFlowNodeInstance,
      FlowElements flowElements,
      ProcessInstanceMapper processInstanceMapper,
      KeyValueStore<VariableKeyDTO, JsonNode> variableStore,
      KeyValueStore<FlowNodeInstanceKeyDTO, FlowNodeInstanceDTO> flowNodeInstanceStore) {
    this.parentScope = parentScope;
    this.subProcessLevel = parentScope != null ? parentScope.getSubProcessLevel() + 1 : 0;
    this.processInstanceId = processInstanceId;
    this.parentFlowNodeInstance = parentFlowNodeInstance;
    this.variableScope = new VariableScope(null, processInstanceId, null, variableStore);
    this.flowNodeInstances =
        new FlowNodeInstances(
            processInstanceId,
            parentFlowNodeInstance,
            flowElements,
            processInstanceMapper,
            flowNodeInstanceStore,
            variableStore);
  }

  public void putInstance(FlowNodeInstance<?> fLowNodeInstance) {
    if (fLowNodeInstance instanceof GatewayInstance<?> gatewayInstance) {
      gatewayInstances.put(
          fLowNodeInstance.getFlowNode().getId(), gatewayInstance.getElementInstanceId());
    }
    flowNodeInstances.putInstance(fLowNodeInstance);
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
        || flowNodeInstances.getInstances().values().stream()
            .filter(WithScope.class::isInstance)
            .map(WithScope.class::cast)
            .anyMatch(instance -> instance.getScope().isStateChanged());
  }

  public void updateActiveCountForInstances() {
    for (FlowNodeInstance<?> instance : flowNodeInstances.getInstances().values()) {
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
        || flowNodeInstances.getInstances().values().stream().anyMatch(FlowNodeInstance::isDirty);
  }

  public FlowElements getFlowElements() {
    return flowNodeInstances.getFlowElements();
  }

  public long nextElementInstanceId() {
    return ++elementInstanceCnt;
  }

  public void addScheduledKey(InstanceScheduleKeyDTO scheduledKey) {
    this.scheduleKeys.add(scheduledKey);
  }

  public Scope selectChildScope(WithScope parentFlowNodeInstance) {
    return new Scope(
        this,
        processInstanceId,
        parentFlowNodeInstance,
        parentFlowNodeInstance.getFlowElements(),
        flowNodeInstances.getProcessInstanceMapper(),
        variableScope.getVariableStore(),
        flowNodeInstances.getFlowNodeInstanceStore());
  }
}
