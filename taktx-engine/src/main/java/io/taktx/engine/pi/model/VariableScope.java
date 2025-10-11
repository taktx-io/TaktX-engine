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
  private final Scope scope;
  protected final KeyValueStore<VariableKeyDTO, JsonNode> variableStore;

  public VariableScope(Scope scope, KeyValueStore<VariableKeyDTO, JsonNode> variableStore) {
    this.scope = scope;
    this.variableStore = variableStore;
  }

  public static VariableScope empty(Scope scope) {
    return new VariableScope(scope, null);
  }

  private FlowNodeInstanceKeyDTO getFlowNodeInstanceKeyForScopePathStart() {
    return new FlowNodeInstanceKeyDTO(scope.getProcessInstanceId(), scope.getScopePath());
  }

  private FlowNodeInstanceKeyDTO getFlowNodeInstanceKeyForScopePathEnd() {
    List<Long> scopePath = scope.getScopePath();

    if (scopePath.isEmpty()) {
      UUID processInstanceIdPlusOne =
          new UUID(
              scope.getProcessInstanceId().getMostSignificantBits(),
              scope.getProcessInstanceId().getLeastSignificantBits() + 1);
      return new FlowNodeInstanceKeyDTO(processInstanceIdPlusOne, scopePath);
    } else {
      Long last = scopePath.getLast();
      last++;
      scopePath.set(scopePath.size() - 1, last);
      return new FlowNodeInstanceKeyDTO(scope.getProcessInstanceId(), scopePath);
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

  public VariablesDTO scopeAndParentsToDto() {
    VariablesDTO dto = VariablesDTO.of(retrieveAllInScope());
    if (scope.getParentScope() != null) {
      VariablesDTO parentVariablesDTO =
          scope.getParentScope().getVariableScope().scopeAndParentsToDto();
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
    if (result == null && scope.getParentScope() != null) {
      result = scope.getParentScope().getVariableScope().get(name);
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
              new FlowNodeInstanceKeyDTO(scope.getProcessInstanceId(), keyPath);
          VariableKeyDTO variableKey = new VariableKeyDTO(flowNodeInstanceKey, key);
          JsonNode value = variables.get(key);
          variableStore.put(variableKey, value);
        });
  }

  public Map<String, JsonNode> retrieveAndFlattenAll() {
    return Map.of();
  }
}
