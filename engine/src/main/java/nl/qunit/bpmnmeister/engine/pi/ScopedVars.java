package nl.qunit.bpmnmeister.engine.pi;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Collection;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.UUID;
import nl.qunit.bpmnmeister.pi.VariablesDTO;

public interface ScopedVars {

  ScopedVars EMPTY = new MockScopedVars(Map.of());

  JsonNode get(String name);

  VariablesDTO getCurrentScopeVariables();

  VariablesDTO select(UUID processInstanceKey);

  VariablesDTO push(UUID processInstanceKey, UUID parentProcessInstanceKey, VariablesDTO vars);

  JsonNode put(String name, JsonNode node);

  VariablesDTO pop();

  void merge(VariablesDTO outputVariables);

  int size();

  Set<String> keySet();

  Collection<JsonNode> values();

  Set<Entry<String, JsonNode>> entrySet();

  class MockScopedVars implements ScopedVars {

    private final Map<String, JsonNode> of;

    public MockScopedVars(Map<String, JsonNode> of) {
      this.of = of;
    }

    public static MockScopedVars of(String name, Object value) {
      return new MockScopedVars(Map.of(name, new ObjectMapper().valueToTree(value)));
    }

    @Override
    public JsonNode get(String name) {
      return of.get(name);
    }

    @Override
    public VariablesDTO getCurrentScopeVariables() {
      return VariablesDTO.of(of);
    }

    @Override
    public VariablesDTO select(UUID processInstanceKey) {
      return VariablesDTO.of(of);
    }

    @Override
    public VariablesDTO push(UUID processInstanceKey, UUID parentProcessInstanceKey, VariablesDTO vars) {
      of.clear();
      of.putAll(vars.getVariables());
      return vars;
    }

    @Override
    public JsonNode put(String name, JsonNode outputCollection1) {
      return of.put(name, outputCollection1);
    }

    @Override
    public VariablesDTO pop() {
      return VariablesDTO.of(of);
    }

    @Override
    public void merge(VariablesDTO outputVariables) {
      of.putAll(outputVariables.getVariables());
    }

    @Override
    public int size() {
      return of.size();
    }

    @Override
    public Set<String> keySet() {
      return of.keySet();
    }

    @Override
    public Collection<JsonNode> values() {
      return of.values();
    }

    @Override
    public Set<Entry<String, JsonNode>> entrySet() {
      return of.entrySet();
    }
  }
}
