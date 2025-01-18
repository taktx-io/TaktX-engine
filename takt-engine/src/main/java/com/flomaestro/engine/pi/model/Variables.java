package com.flomaestro.engine.pi.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.flomaestro.takt.dto.v_1_0_0.VariablesDTO;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.function.Function;

public class Variables {

  private final Map<String, JsonNode> variables;
  private final Function<String, JsonNode> variableProvider;
  private final Set<String> dirty;

  public Variables(Function<String, JsonNode> variableProvider) {
    this.variables = new HashMap<>();
    this.variableProvider = variableProvider;
    this.dirty = new HashSet<>();
  }

  public static Variables empty() {
    return new Variables(key -> null);
  }

  public static Variables empty(Function<String, JsonNode> variableProvider) {
    return new Variables(variableProvider);
  }

  public JsonNode get(String key) {
    if (variables.containsKey(key)) {
      return variables.get(key);
    } else {
      JsonNode value = variableProvider.apply(key);
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
}
