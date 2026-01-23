/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks All rights reserved.
 * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 * Free use is permitted with up to 3 Kafka partitions per topic. See LICENSE file for details.
 * For commercial use or more partitions and features, contact [https://www.taktx.io/contact].
 */

package io.taktx.engine.pi.model;

import com.fasterxml.jackson.databind.JsonNode;
import io.taktx.dto.FlowNodeInstanceKeyDTO;
import io.taktx.dto.VariableKeyDTO;
import io.taktx.dto.VariablesDTO;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.Getter;
import org.apache.kafka.streams.state.KeyValueIterator;
import org.apache.kafka.streams.state.KeyValueStore;

@Getter
public class VariableScope {
  private final HashMap<String, JsonNode> variables = new HashMap<>();
  private final Set<String> dirtyVariables = new HashSet<>();
  private final VariableScope parentScope;
  private final Map<List<Long>, VariableScope> childScopes = new HashMap<>();
  private final IFlowNodeInstance flowNodeInstance;
  private final UUID processInstanceId;
  private final KeyValueStore<VariableKeyDTO, JsonNode> variableStore;

  public VariableScope(
      VariableScope parentScope,
      IFlowNodeInstance flowNodeInstance,
      UUID processInstanceId,
      KeyValueStore<VariableKeyDTO, JsonNode> variableStore) {
    this.parentScope = parentScope;
    this.flowNodeInstance = flowNodeInstance;
    this.processInstanceId = processInstanceId;
    this.variableStore = variableStore;
  }

  public static VariableScope empty(
      UUID processInstanceId, KeyValueStore<VariableKeyDTO, JsonNode> variableStore) {
    return new VariableScope(null, null, processInstanceId, variableStore);
  }

  private FlowNodeInstanceKeyDTO getFlowNodeInstanceKeyForScopePathStart() {
    List<Long> path = flowNodeInstance != null ? flowNodeInstance.createKeyPath() : List.of();
    return new FlowNodeInstanceKeyDTO(processInstanceId, path);
  }

  private FlowNodeInstanceKeyDTO getFlowNodeInstanceKeyForScopePathEnd() {
    List<Long> path = flowNodeInstance != null ? flowNodeInstance.createKeyPath() : List.of();
    List<Long> scopePath = new ArrayList<>(path);

    if (scopePath.isEmpty()) {
      UUID processInstanceIdPlusOne =
          new UUID(
              processInstanceId.getMostSignificantBits(),
              processInstanceId.getLeastSignificantBits() + 1);
      return new FlowNodeInstanceKeyDTO(processInstanceIdPlusOne, scopePath);
    } else {
      Long last = scopePath.getLast();
      last++;
      scopePath.set(scopePath.size() - 1, last);
      return new FlowNodeInstanceKeyDTO(processInstanceId, scopePath);
    }
  }

  public void put(String key, JsonNode value) {
    dirtyVariables.add(key);
    variables.put(key, value);
  }

  public void merge(VariablesDTO variables) {
    dirtyVariables.addAll(variables.getVariables().keySet());
    this.variables.putAll(variables.getVariables());
  }

  public VariablesDTO scopeToDTO() {
    Map<String, JsonNode> dirtyVariablesMap = new HashMap<>(variables);
    getDirtyVariables().forEach(key -> dirtyVariablesMap.put(key, variables.get(key)));
    return new VariablesDTO(dirtyVariablesMap);
  }

  public Map<String, JsonNode> retrieveAndFlattenAllVariables() {
    Map<String, JsonNode> flattened = new HashMap<>(retrieveAllInScope());

    childScopes.forEach(
        (k, childScope) -> flattened.putAll(childScope.retrieveAndFlattenAllVariables()));
    return flattened;
  }

  public Map<String, JsonNode> retrieveAllInScope() {

    FlowNodeInstanceKeyDTO startflowNodeInstanceKeyForScopePath =
        getFlowNodeInstanceKeyForScopePathStart();
    FlowNodeInstanceKeyDTO endflowNodeInstanceKeyForScopePath =
        getFlowNodeInstanceKeyForScopePathEnd();
    VariableKeyDTO start = new VariableKeyDTO(startflowNodeInstanceKeyForScopePath, "");

    VariableKeyDTO end = new VariableKeyDTO(endflowNodeInstanceKeyForScopePath, "");

    try (KeyValueIterator<VariableKeyDTO, JsonNode> range = variableStore.range(start, end)) {
      range.forEachRemaining(
          kv -> {
            if (!variables.containsKey(kv.key.getVariableName())) {
              variables.put(kv.key.getVariableName(), kv.value);
            }
          });
    }
    return variables;
  }

  public JsonNode get(String name) {
    JsonNode result = null;
    if (variables.containsKey(name)) {
      result = variables.get(name);
    }
    if (result == null && variableStore != null) {
      VariableKeyDTO k = new VariableKeyDTO(getFlowNodeInstanceKeyForScopePathStart(), name);
      result = variableStore.get(k);
    }
    if (result == null && parentScope != null) {
      result = parentScope.get(name);
    }
    return result;
  }

  private void persistScope(
      UUID processInstanceId, KeyValueStore<VariableKeyDTO, JsonNode> variableStore) {
    dirtyVariables.forEach(
        key -> {
          List<Long> path = flowNodeInstance != null ? flowNodeInstance.createKeyPath() : List.of();
          FlowNodeInstanceKeyDTO flowNodeInstanceKey =
              new FlowNodeInstanceKeyDTO(processInstanceId, path);
          VariableKeyDTO variableKey = new VariableKeyDTO(flowNodeInstanceKey, key);
          JsonNode value = variables.get(key);
          variableStore.put(variableKey, value);
        });
  }

  public void persistTree(
      UUID processInstanceId, KeyValueStore<VariableKeyDTO, JsonNode> variableStore) {
    persistScope(processInstanceId, variableStore);
    for (VariableScope childScope : childScopes.values()) {
      childScope.persistTree(processInstanceId, variableStore);
    }
  }

  public VariableScope selectChildScope(IFlowNodeInstance instanceWithInstanceId) {
    return this.childScopes.computeIfAbsent(
        instanceWithInstanceId.createKeyPath(),
        k -> new VariableScope(this, instanceWithInstanceId, processInstanceId, variableStore));
  }

  public VariablesDTO scopeAndParentsToDto() {
    VariablesDTO dto = VariablesDTO.ofJsonMap(retrieveAllInScope());
    if (parentScope != null) {
      VariablesDTO parentVariablesDTO = parentScope.scopeAndParentsToDto();
      parentVariablesDTO
          .getVariables()
          .forEach(
              (key, value) -> {
                if (dto.get(key) == null) {
                  dto.put(key, value);
                }
              });
    }
    return dto;
  }
}
