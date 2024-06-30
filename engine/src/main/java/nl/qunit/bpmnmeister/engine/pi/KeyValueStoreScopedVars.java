package nl.qunit.bpmnmeister.engine.pi;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;
import java.util.UUID;
import nl.qunit.bpmnmeister.pi.Variables;
import org.apache.kafka.streams.state.KeyValueStore;

public class KeyValueStoreScopedVars implements ScopedVars {

  private final KeyValueStore<UUID, VariablesParentPair> variableStore;

  private UUID currentScopeKey;
  private VariablesParentPair currentScope;

  public KeyValueStoreScopedVars(KeyValueStore<UUID, VariablesParentPair> variableStore) {
    this.variableStore = variableStore;
  }

  @Override
  public Variables select(UUID processInstanceKey) {
    currentScope = variableStore.get(processInstanceKey);
    currentScopeKey = processInstanceKey;
    return currentScope.getVariables();
  }

  @Override
  public Variables push(UUID processInstanceKey, UUID parentProcessInstanceKey, Variables vars) {
    currentScope = new VariablesParentPair(parentProcessInstanceKey, vars);
    currentScopeKey = processInstanceKey;
    variableStore.put(processInstanceKey, currentScope);
    return vars;
  }

  @Override
  public Variables pop() {
    variableStore.delete(currentScopeKey);
    return select(currentScope.getParent());
  }

  @Override
  public void merge(Variables outputVariables) {
    currentScope.getVariables().merge(outputVariables);
    variableStore.put(currentScopeKey, currentScope);
  }

  public Variables getCurrentScopeVariables() {
    return currentScope.getVariables();
  }

  @Override
  public int size() {
    VariablesParentPair countScope = currentScope;
    int size = 0;
    while (countScope != null) {
      size += countScope.getVariables().getVariables().size();
      countScope = variableStore.get(countScope.getParent());
    }
    return size;
  }

  @Override
  public Set<String> keySet() {
    Set<String> keys = new HashSet<>();
    VariablesParentPair scope = currentScope;
    while (scope != null) {
      keys.addAll(scope.getVariables().getVariables().keySet());
      scope = variableStore.get(scope.getParent());
    }
    return keys;
  }

  @Override
  public Collection<JsonNode> values() {
    List<JsonNode> values = new ArrayList<>();
    VariablesParentPair scope = currentScope;
    while (scope != null) {
      values.addAll(scope.getVariables().getVariables().values());
      scope = variableStore.get(scope.getParent());
    }

    return values;
  }

  @Override
  public Set<Entry<String, JsonNode>> entrySet() {
    Set<Entry<String, JsonNode>> entries = new HashSet<>();
    VariablesParentPair scope = currentScope;
    while (scope != null) {
      entries.addAll(scope.getVariables().getVariables().entrySet());
      scope = variableStore.get(scope.getParent());
    }
    return entries;
  }

  public JsonNode get(String name) {
    JsonNode jsonNode = currentScope.getVariables().get(name);
    if (jsonNode == null) {
      return getFromParent(currentScope.getParent(), name);
    }
    return jsonNode;
  }

  private JsonNode getFromParent(UUID parent, String name) {
    if (parent != null) {
      VariablesParentPair parentScope = variableStore.get(parent);
      if (parentScope == null) {
        return null;
      }
      JsonNode jsonNode = parentScope.getVariables().get(name);
      if (jsonNode != null) {
        return jsonNode;
      } else {
        getFromParent(parentScope.getParent(), name);
      }
    }
    return null;
  }

  @Override
  public JsonNode put(String name, JsonNode node) {
    JsonNode put = currentScope.getVariables().put(name, node);
    variableStore.put(currentScopeKey, currentScope);
    return put;
  }
}
