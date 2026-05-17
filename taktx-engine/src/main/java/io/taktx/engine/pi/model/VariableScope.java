/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */

package io.taktx.engine.pi.model;

import com.fasterxml.jackson.databind.JsonNode;
import io.taktx.dto.FlowNodeInstanceKeyDTO;
import io.taktx.dto.VariableKeyDTO;
import io.taktx.dto.VariablesDTO;
import io.taktx.engine.pi.VariableValueJsonMapper;
import io.taktx.proto.VariableValue;
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
  private final HashMap<String, VariableValue> variables = new HashMap<>();
  private final Set<String> dirtyVariables = new HashSet<>();
  private final VariableScope parentScope;
  private final Map<List<Long>, VariableScope> childScopes = new HashMap<>();
  private final IFlowNodeInstance flowNodeInstance;
  private final UUID processInstanceId;
  private final KeyValueStore<VariableKeyDTO, VariableValue> variableStore;

  public VariableScope(
      VariableScope parentScope,
      IFlowNodeInstance flowNodeInstance,
      UUID processInstanceId,
      KeyValueStore<VariableKeyDTO, VariableValue> variableStore) {
    this.parentScope = parentScope;
    this.flowNodeInstance = flowNodeInstance;
    this.processInstanceId = processInstanceId;
    this.variableStore = variableStore;
  }

  public static VariableScope empty(
      UUID processInstanceId, KeyValueStore<VariableKeyDTO, VariableValue> variableStore) {
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

  public void put(String key, VariableValue value) {
    dirtyVariables.add(key);
    variables.put(key, value);
  }

  public void put(String key, JsonNode value) {
    put(key, VariableValueJsonMapper.toVariableValue(value));
  }

  public void merge(VariablesDTO variables) {
    merge(VariableValueJsonMapper.toVariableMap(variables));
  }

  public void merge(Map<String, VariableValue> variables) {
    dirtyVariables.addAll(variables.keySet());
    this.variables.putAll(variables);
  }

  public VariablesDTO scopeToDTO() {
    Map<String, VariableValue> dirtyVariablesMap = new HashMap<>(variables);
    getDirtyVariables().forEach(key -> dirtyVariablesMap.put(key, variables.get(key)));
    return VariableValueJsonMapper.toVariablesDTO(dirtyVariablesMap);
  }

  public Map<String, VariableValue> retrieveAndFlattenAllVariables() {
    Map<String, VariableValue> flattened = new HashMap<>(retrieveAllInScope());

    childScopes.forEach(
        (k, childScope) -> flattened.putAll(childScope.retrieveAndFlattenAllVariables()));
    return flattened;
  }

  public Map<String, VariableValue> retrieveAllInScope() {

    FlowNodeInstanceKeyDTO startflowNodeInstanceKeyForScopePath =
        getFlowNodeInstanceKeyForScopePathStart();
    FlowNodeInstanceKeyDTO endflowNodeInstanceKeyForScopePath =
        getFlowNodeInstanceKeyForScopePathEnd();
    VariableKeyDTO start = new VariableKeyDTO(startflowNodeInstanceKeyForScopePath, "");

    VariableKeyDTO end = new VariableKeyDTO(endflowNodeInstanceKeyForScopePath, "");

    try (KeyValueIterator<VariableKeyDTO, VariableValue> range = variableStore.range(start, end)) {
      range.forEachRemaining(
          kv -> {
            if (!variables.containsKey(kv.key.getVariableName())) {
              variables.put(kv.key.getVariableName(), kv.value);
            }
          });
    }
    return variables;
  }

  public VariableValue get(String name) {
    VariableValue result = null;
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
      UUID processInstanceId, KeyValueStore<VariableKeyDTO, VariableValue> variableStore) {
    dirtyVariables.forEach(
        key -> {
          List<Long> path = flowNodeInstance != null ? flowNodeInstance.createKeyPath() : List.of();
          FlowNodeInstanceKeyDTO flowNodeInstanceKey =
              new FlowNodeInstanceKeyDTO(processInstanceId, path);
          VariableKeyDTO variableKey = new VariableKeyDTO(flowNodeInstanceKey, key);
          VariableValue value = variables.get(key);
          variableStore.put(variableKey, value);
        });
  }

  public void persistTree(
      UUID processInstanceId, KeyValueStore<VariableKeyDTO, VariableValue> variableStore) {
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
    VariablesDTO dto = VariableValueJsonMapper.toVariablesDTO(retrieveAllInScope());
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
