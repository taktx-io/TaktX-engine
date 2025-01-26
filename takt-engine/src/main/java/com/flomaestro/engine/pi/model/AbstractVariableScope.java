package com.flomaestro.engine.pi.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flomaestro.takt.dto.v_1_0_0.VariableKeyDTO;
import com.flomaestro.takt.dto.v_1_0_0.VariablesDTO;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.Getter;
import org.apache.kafka.streams.state.KeyValueStore;

@Getter
public abstract class AbstractVariableScope {

  private final HashMap<String, JsonNode> variables = new HashMap<>();
  private final Set<String> dirtyVariables = new HashSet<>();
  protected final KeyValueStore<VariableKeyDTO, JsonNode> variableStore;
  private final UUID processInstanceKey;
  private final UUID flowNodeInstancesKey;
  private final UUID elementInstanceKey;
  private final AbstractVariableScope parentScope;
  private final ObjectMapper objectMapper = new ObjectMapper();

  protected AbstractVariableScope(
      KeyValueStore<VariableKeyDTO, JsonNode> variableStore,
      UUID processInstanceKey,
      UUID flowNodeInstancesKey,
      UUID elementInstanceKey,
      AbstractVariableScope parentScope
  ) {
    this.variableStore = variableStore;
    this.processInstanceKey = processInstanceKey;
    this.flowNodeInstancesKey = flowNodeInstancesKey;
    this.elementInstanceKey = elementInstanceKey;
    this.parentScope = parentScope;
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
      dto.getVariables().putAll(parentScope.scopeAndParentsToDto().getVariables());
    }
    return dto;
  }

  Map<String, JsonNode> persistScope() {
    Map<String, JsonNode> stored = new HashMap<>();
    dirtyVariables.forEach(
        key -> {
          VariableKeyDTO variableKey =
              new VariableKeyDTO(processInstanceKey, flowNodeInstancesKey, elementInstanceKey, key);
          JsonNode value = variables.get(key);
          variableStore.put(variableKey, value);
          stored.put(key, value);
        });
    return stored;
  }

  public Map<String, JsonNode> retrieveAllInScope() {
    if (variableStore != null) {
      VariableKeyDTO start = new VariableKeyDTO(processInstanceKey, flowNodeInstancesKey, elementInstanceKey, "");
      VariableKeyDTO end = new VariableKeyDTO(processInstanceKey, flowNodeInstancesKey, elementInstanceKey, "\u00FF");
      variableStore.range(start, end).forEachRemaining(kv -> {
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
      result =
          variableStore.get(
              new VariableKeyDTO(
                  processInstanceKey, flowNodeInstancesKey, elementInstanceKey, name));
    }
    if (result == null && parentScope != null) {
      result = parentScope.get(name);
    }
    return result;
  }

  public Set<String> keySet() {
    return variables.keySet();
  }

}
