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
import io.taktx.engine.pd.model.WIthChildElements;
import io.taktx.engine.pi.DirectInstanceResult;
import io.taktx.engine.pi.ProcessInstanceMapper;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
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
  private UUID processInstanceId;
  private VariableScope variableScope;
  private KeyValueStore<VariableKeyDTO, JsonNode> variableStore;
  private FlowNodeInstances flowNodeInstances;
  private KeyValueStore<FlowNodeInstanceKeyDTO, FlowNodeInstanceDTO> flowNodeInstanceStore;
  private ExecutionState initialState;
  private boolean stateChanged = false;
  private WithScope parentFlowNodeInstance;

  private final DirectInstanceResult directInstanceResult = DirectInstanceResult.empty();
  private final Map<Long, Long> boundaryEventToActivity = new java.util.WeakHashMap<>();

  private int subProcessLevel;
  private int activeCnt = 0;
  private ExecutionState state = ExecutionState.INITIALIZED;
  private long elementInstanceCnt = 0;
  private Map<String, Long> gatewayInstances = new HashMap<>();
  private Map<String, Set<String>> messageSubscriptions = new HashMap<>();
  private Set<InstanceScheduleKeyDTO> scheduleKeys = new HashSet<>();

  private final Map<Long, Set<Long>> activityToBoundaryEvents = new java.util.WeakHashMap<>();
  private ProcessInstanceMapper processInstanceMapper;
  private FlowElements flowElements;

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
    this.processInstanceMapper = processInstanceMapper;
    this.flowElements = flowElements;
    this.variableScope = new VariableScope(this, variableStore);
    this.variableStore = variableStore;
    this.flowNodeInstances = new FlowNodeInstances(this, flowNodeInstanceStore);
    this.flowNodeInstanceStore = flowNodeInstanceStore;
  }

  public void putInstance(FlowNodeInstance<?> fLowNodeInstance) {
    if (fLowNodeInstance instanceof GatewayInstance<?> gatewayInstance) {
      gatewayInstances.put(
          fLowNodeInstance.getFlowNode().getId(), gatewayInstance.getElementInstanceId());
    }
    flowNodeInstances.putInstance(fLowNodeInstance);
  }

  public List<Long> getScopePath() {
    LinkedList<Long> path = new LinkedList<>();
    addScopeToPath(path);
    return path;
  }

  public void addScopeToPath(LinkedList<Long> path) {
    if (parentFlowNodeInstance != null) {
      path.addFirst(parentFlowNodeInstance.getElementInstanceId());
    }
    if (parentScope != null) {
      parentScope.addScopeToPath(path);
    }
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
      if (instance.isCounted()) {
        continue;
      }
      instance.setCounted(true);
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

  public long nextElementInstanceId() {
    return ++elementInstanceCnt;
  }

  public void addScheduledKey(InstanceScheduleKeyDTO scheduledKey) {
    this.scheduleKeys.add(scheduledKey);
  }

  public Scope selectChildScope(WithScope parentFlowNodeInstance, FlowElements flowElements) {
    return new Scope(
        this,
        processInstanceId,
        parentFlowNodeInstance,
        flowElements,
        processInstanceMapper,
        variableScope.getVariableStore(),
        flowNodeInstances.getFlowNodeInstanceStore());
  }

  public FlowNodeInstance<?> mapToFlowNodeInstance(FlowNodeInstanceDTO value) {
    FlowNodeInstance<?> instance = processInstanceMapper.map(value, getFlowElements());
    instance.setParentInstance(parentFlowNodeInstance);
    if (instance instanceof WithScope withScope) {
      Scope childScope = withScope.getScope();
      childScope.setParentScope(this);
      childScope.setFlowNodeInstanceStore(this.getFlowNodeInstanceStore());
      childScope.setVariableStore(this.getVariableStore());
      childScope.setParentFlowNodeInstance(withScope);
      childScope.setProcessInstanceMapper(processInstanceMapper);
      childScope.setProcessInstanceId(processInstanceId);
      VariableScope childVariableScope = new VariableScope(childScope, variableStore);
      childScope.setVariableScope(childVariableScope);
      FlowElements elements =
          instance.getFlowNode() instanceof WIthChildElements withChildElements
              ? withChildElements.getElements()
              : flowElements;
      childScope.setFlowElements(elements);
      childScope.setFlowNodeInstances(new FlowNodeInstances(childScope, flowNodeInstanceStore));
      childScope.setParentFlowNodeInstance(withScope);
    }
    return instance;
  }

  public void persistVariables() {
    variableScope.persist();
    flowNodeInstances
        .getInstances()
        .values()
        .forEach(
            instance -> {
              if (instance instanceof WithScope withScope) {
                withScope.getScope().persistVariables();
              }
            });
  }

  public Map<String, JsonNode> retrieveAndFlattenAllVariables() {
    Map<String, JsonNode> variables = new HashMap<>(variableScope.retrieveAllInScope());
    flowNodeInstances
        .getAllInstances()
        .values()
        .forEach(
            instance -> {
              if (instance instanceof WithScope withScope) {
                variables.putAll(withScope.getScope().retrieveAndFlattenAllVariables());
              }
            });
    return variables;
  }
}
