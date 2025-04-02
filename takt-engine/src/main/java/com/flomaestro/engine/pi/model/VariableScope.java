/*
 *
 *  * TaktX - A high-performance BPMN engine
 *  * Copyright (c) 2025 TaktX B.V. All rights reserved.
 *  * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 *  * Free use is permitted with up to 3 Kafka partitions. See LICENSE file for details.
 *  * For commercial use or more partitions and features, contact [info@taktx.io] or [https://www.taktx.io/contact].
 *
 */

package com.flomaestro.engine.pi.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.flomaestro.takt.dto.v_1_0_0.FlowNodeInstanceKeyDTO;
import com.flomaestro.takt.dto.v_1_0_0.VariableKeyDTO;
import com.flomaestro.takt.dto.v_1_0_0.VariablesDTO;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
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
  private final Map<Long, VariableScope> childScopes = new HashMap<>();
  private final Set<String> dirtyVariables = new HashSet<>();
  protected final KeyValueStore<VariableKeyDTO, JsonNode> variableStore;
  private final UUID processInstanceKey;
  private final Long elementInstanceKey;
  private final VariableScope parentScope;

  public VariableScope(
      KeyValueStore<VariableKeyDTO, JsonNode> variableStore,
      UUID processInstanceKey,
      Long elementInstanceKey,
      VariableScope parentScope) {
    this.variableStore = variableStore;
    this.processInstanceKey = processInstanceKey;
    this.elementInstanceKey = elementInstanceKey;
    this.parentScope = parentScope;
  }

  public static VariableScope empty() {
    return new VariableScope(null, null, null, null);
  }

  private FlowNodeInstanceKeyDTO getFlowNodeInstanceKeyForScopePathStart() {
    return new FlowNodeInstanceKeyDTO(processInstanceKey, getScopePath());
  }

  private FlowNodeInstanceKeyDTO getFlowNodeInstanceKeyForScopePathEnd() {
    List<Long> scopePath = getScopePath();

    if (scopePath.isEmpty()) {
      UUID processInstanceKeyPlusOne =
          new UUID(
              processInstanceKey.getMostSignificantBits(),
              processInstanceKey.getLeastSignificantBits() + 1);
      return new FlowNodeInstanceKeyDTO(processInstanceKeyPlusOne, scopePath);
    } else {
      Long last = scopePath.getLast();
      last++;
      scopePath.set(scopePath.size() - 1, last);
      return new FlowNodeInstanceKeyDTO(processInstanceKey, scopePath);
    }
  }

  private List<Long> getScopePath() {
    LinkedList<Long> path = new LinkedList<>();
    addScopeToPath(path);
    return path;
  }

  private void addScopeToPath(LinkedList<Long> path) {
    if (elementInstanceKey != null) {
      path.addFirst(elementInstanceKey);
    }
    if (parentScope != null) {
      parentScope.addScopeToPath(path);
    }
  }

  public VariableScope selectFlowNodeInstancesScope(long flowNodeInstanceKey) {
    return this.childScopes.computeIfAbsent(
        flowNodeInstanceKey,
        k -> new VariableScope(variableStore, processInstanceKey, flowNodeInstanceKey, this));
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

  public VariablesDTO scopeAndParentsToDto() {
    VariablesDTO dto = VariablesDTO.of(retrieveAllInScope());
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

  public Set<String> keySet() {
    return variables.keySet();
  }

  public void persist() {
    persistScope(List.of());
  }

  private void persistScope(List<Long> keyPath) {
    dirtyVariables.forEach(
        key -> {
          FlowNodeInstanceKeyDTO flowNodeInstanceKey =
              new FlowNodeInstanceKeyDTO(processInstanceKey, keyPath);
          VariableKeyDTO variableKey = new VariableKeyDTO(flowNodeInstanceKey, key);
          JsonNode value = variables.get(key);
          variableStore.put(variableKey, value);
        });
    childScopes.forEach(
        (k, v) -> {
          List<Long> newPath = new ArrayList<>(keyPath);
          newPath.add(k);
          v.persistScope(newPath);
        });
  }

  public Map<String, JsonNode> retrieveAndFlattenAll() {
    Map<String, JsonNode> retrieved = retrieveAllInScope();
    childScopes.values().forEach(scope -> retrieved.putAll(scope.retrieveAndFlattenAll()));
    return retrieved;
  }

  public Map<String, JsonNode> retrieveAllInScope() {
    if (variableStore != null) {

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
    }
    return variables;
  }

  public void mergeAllToParent() {
    if (parentScope != null) {
      parentScope.merge(scopeToDTO());
      clearScope();
    }
  }

  private void clearScope() {
    variables.clear();
    dirtyVariables.clear();
  }

  public void putInParent(String varName, JsonNode jsonNode) {
    if (parentScope != null) {
      parentScope.put(varName, jsonNode);
    }
  }

  public void remove(String key) {
    variables.remove(key);
    dirtyVariables.remove(key);
  }
}
