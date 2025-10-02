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
  private final VariableScope parentVariableScope;
  private final Map<Long, VariableScope> childScopes = new HashMap<>();
  private final HashMap<String, JsonNode> variables = new HashMap<>();
  private final Set<String> dirtyVariables = new HashSet<>();
  protected final KeyValueStore<VariableKeyDTO, JsonNode> variableStore;
  private final UUID processInstanceId;
  private final Long elementInstanceKey;

  public VariableScope(
      UUID processInstanceId, KeyValueStore<VariableKeyDTO, JsonNode> variableStore) {
    this(null, processInstanceId, null, variableStore);
  }

  public VariableScope(
      VariableScope parentVariableScope,
      UUID processInstanceId,
      Long elementInstanceKey,
      KeyValueStore<VariableKeyDTO, JsonNode> variableStore) {
    this.parentVariableScope = parentVariableScope;
    this.processInstanceId = processInstanceId;
    this.elementInstanceKey = elementInstanceKey;
    this.variableStore = variableStore;
  }

  public static VariableScope empty() {
    return new VariableScope(null, null, null, null);
  }

  private FlowNodeInstanceKeyDTO getFlowNodeInstanceKeyForScopePathStart() {
    return new FlowNodeInstanceKeyDTO(processInstanceId, getScopePath());
  }

  private FlowNodeInstanceKeyDTO getFlowNodeInstanceKeyForScopePathEnd() {
    List<Long> scopePath = getScopePath();

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

  private List<Long> getScopePath() {
    LinkedList<Long> path = new LinkedList<>();
    addScopeToPath(path);
    return path;
  }

  private void addScopeToPath(LinkedList<Long> path) {
    if (elementInstanceKey != null) {
      path.addFirst(elementInstanceKey);
    }
    if (parentVariableScope != null) {
      parentVariableScope.addScopeToPath(path);
    }
  }

  public VariableScope selectChildScope(long flowNodeInstanceKey) {
    return new VariableScope(this, processInstanceId, flowNodeInstanceKey, variableStore);
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
    if (parentVariableScope != null) {
      VariablesDTO parentVariablesDTO = parentVariableScope.scopeAndParentsToDto();
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

  public void remove(String key) {
    variables.remove(key);
    dirtyVariables.add(key);
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
    if (result == null && parentVariableScope != null) {
      result = parentVariableScope.get(name);
    }
    return result;
  }

  public void persist() {
    persistScope(List.of());
  }

  private void persistScope(List<Long> keyPath) {
    dirtyVariables.forEach(
        key -> {
          FlowNodeInstanceKeyDTO flowNodeInstanceKey =
              new FlowNodeInstanceKeyDTO(processInstanceId, keyPath);
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
    return Map.of();
  }
}
