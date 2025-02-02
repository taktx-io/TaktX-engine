package com.flomaestro.engine.pi.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flomaestro.takt.dto.v_1_0_0.FlowNodeInstanceKeyDTO;
import com.flomaestro.takt.dto.v_1_0_0.VariableKeyDTO;
import com.flomaestro.takt.dto.v_1_0_0.VariablesDTO;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.Getter;
import org.apache.kafka.streams.state.KeyValueStore;

@Getter
public class VariableScope {
  private static final ObjectMapper objectMapper = new ObjectMapper();
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

  private FlowNodeInstanceKeyDTO getFlowNodeInstanceKeyForScopePath() {
    return new FlowNodeInstanceKeyDTO(processInstanceKey, getScopePath());
  }

  private List<Long> getScopePath() {
    LinkedList<Long> path = new LinkedList<>();
    addScopeToPath(path);
    return path;
  }

  private void addScopeToPath(LinkedList<Long> path) {
    path.addFirst(elementInstanceKey);
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
    return new VariablesDTO(variables);
  }

  public VariablesDTO scopeAndParentsToDto() {
    VariablesDTO dto = scopeToDTO();
    if (parentScope != null) {
      VariablesDTO parentVariablesDTO = parentScope.scopeAndParentsToDto();
      parentVariablesDTO.getVariables().entrySet().forEach(e -> {
            if (dto.get(e.getKey()) == null) {
              dto.put(e.getKey(), e.getValue());
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
      result =
          variableStore.get(
              new VariableKeyDTO(getFlowNodeInstanceKeyForScopePath(), name));
    }
    if (result == null && parentScope != null) {
      result = parentScope.get(name);
    }
    return result;
  }

  public Set<String> keySet() {
    return variables.keySet();
  }

  public Map<String, JsonNode> persist() {
    Map<String, JsonNode> stored = persistScope();
    childScopes.values().forEach(variable -> stored.putAll(variable.persist()));
    return stored;
  }

  private Map<String, JsonNode> persistScope() {
    Map<String, JsonNode> stored = new HashMap<>();
    dirtyVariables.forEach(
        key -> {
          FlowNodeInstanceKeyDTO flowNodeInstanceKey = new FlowNodeInstanceKeyDTO(processInstanceKey, List.of());
          VariableKeyDTO variableKey =
              new VariableKeyDTO(flowNodeInstanceKey, key);
          JsonNode value = variables.get(key);
          variableStore.put(variableKey, value);
          stored.put(key, value);
        });
    return stored;
  }

  public Map<String, JsonNode> retrieveAndFlattenAll() {
    Map<String, JsonNode> retrieved = retrieveAllInScope();
    childScopes
        .values()
        .forEach(scope -> retrieved.putAll(scope.retrieveAndFlattenAll()));
    return retrieved;
  }

  public Map<String, JsonNode> retrieveAllInScope() {
    if (variableStore != null) {

      VariableKeyDTO start =
          new VariableKeyDTO(getFlowNodeInstanceKeyForScopePath(), "");
      VariableKeyDTO end =
          new VariableKeyDTO(
              getFlowNodeInstanceKeyForScopePath(), "\u00FF");
      variableStore
          .range(start, end)
          .forEachRemaining(
              kv -> {
                if (!variables.containsKey(kv.key.getVariableName())) {
                  variables.put(kv.key.getVariableName(), kv.value);
                }
              });
    }
    return variables;
  }

}
