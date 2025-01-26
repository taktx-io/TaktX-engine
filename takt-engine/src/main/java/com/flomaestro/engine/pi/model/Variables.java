package com.flomaestro.engine.pi.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.flomaestro.engine.pi.VariablesProvider;
import com.flomaestro.takt.dto.v_1_0_0.VariablesDTO;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

public class Variables {
  private final Variables parentScope;
  private final Map<String, JsonNode> variables;
  private final VariablesProvider variableProvider;
  private final Set<String> dirty;

  public Variables(VariablesProvider variableProvider) {
    this(variableProvider, null);
  }

  public Variables(VariablesProvider variableProvider, Variables parentScope) {
    this.variables = new HashMap<>();
    this.variableProvider = variableProvider;
    this.dirty = new HashSet<>();
    this.parentScope = parentScope;
  }

  public Variables addScope() {
    return new Variables(variableProvider, this);
  }

  public static Variables empty(VariablesProvider variableProvider) {
    return new Variables(variableProvider);
  }

  public JsonNode get(String key) {
    if (variables.containsKey(key)) {
      return variables.get(key);
    } else if (parentScope != null) {
      return parentScope.get(key);
    } else {
      JsonNode value = variableProvider.get(key);
      if (value != null) {
        variables.put(key, value);
        return value;
      } else {
        return null;
      }
    }
  }

  public JsonNode put(String key, JsonNode value) {
    dirty.add(key);
    return variables.put(key, value);
  }

  public void merge(VariablesDTO variablesToMerge) {
    dirty.addAll(variablesToMerge.getVariables().keySet());
    variables.putAll(variablesToMerge.getVariables());
  }

  public void merge(Variables variablesToMerge) {
    dirty.addAll(variablesToMerge.keySet());
    variables.putAll(variablesToMerge.variables);
  }

  public int size() {
    return variables.size();
  }

  public boolean isDirty(String key) {
    return dirty.contains(key);
  }

  public Set<String> keySet() {
    return variables.keySet();
  }

  public Set<Entry<String, JsonNode>> entrySet() {
    return variables.entrySet();
  }

  public Variables flattenedAndRetrievedAll() {
    Variables variables = new Variables(variableProvider);
    flatten(variables, this);
    variables.retrieveAllStoredVariables();
    return variables;
  }

  public void retrieveAllStoredVariables() {
    Map<String, JsonNode> all = variableProvider.getAll();
    all.entrySet()
        .forEach(
            entry -> {
              // Make sure we don't overwrite existing variables
              if (!variables.containsKey(entry.getKey())) {
                variables.put(entry.getKey(), entry.getValue());
              }
            });
  }

  public void flatten(Variables target, Variables source) {
    target.merge(source);
    if (source.parentScope != null) {
      flatten(target, source.parentScope);
    }
  }

  public Variables flatten() {
    Variables flattenedVariables = new Variables(variableProvider);
    flatten(flattenedVariables, this);
    return flattenedVariables;
  }
}
