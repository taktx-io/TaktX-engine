package nl.qunit.bpmnmeister.engine.feel;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.NullNode;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import nl.qunit.bpmnmeister.engine.pi.model.Variables;

public class WrappedInMap implements Map<String, Object> {

  private final Variables vars;
  private final ObjectMapper objectMapper;

  private WrappedInMap(Variables vars, ObjectMapper objectMapper) {
    this.vars = vars;
    this.objectMapper = objectMapper;
  }

  public static Map<String, Object> of(Variables variables, ObjectMapper objectMapper) {
    return new WrappedInMap(variables, objectMapper);
  }

  @Override
  public int size() {
    return vars.size();
  }

  @Override
  public boolean isEmpty() {
    return size() == 0;
  }

  @Override
  public boolean containsKey(Object key) {
    return false;
  }

  @Override
  public boolean containsValue(Object value) {
    return false;
  }

  @Override
  public Object get(Object key) {
    return vars.get(key.toString());
  }

  @Override
  public Object put(String key, Object value) {
    JsonNode node = objectMapper.valueToTree(value);
    return vars.put(key, node);
  }

  @Override
  public Object remove(Object key) {
    return vars.remove(key.toString());
  }

  @Override
  public void putAll(Map<? extends String, ?> m) {
    m.forEach((key, value) -> put(key, objectMapper.valueToTree(value)));
  }

  @Override
  public void clear() {
    vars.clear();
  }

  @Override
  public Set<String> keySet() {
    return vars.keySet();
  }

  @Override
  public Collection<Object> values() {
    return vars.values().stream().map(this::toObject).toList();
  }

  private Object toObject(JsonNode v) {
    try {
      return objectMapper.treeToValue(v, Object.class);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException(e);
    }
  }

  @Override
  public Set<Entry<String, Object>> entrySet() {
    return vars.entrySet().stream()
        .filter(
            e -> e.getKey() != null && e.getValue() != null && !(e.getValue() instanceof NullNode))
        .map(e -> Map.entry(e.getKey(), toObject(e.getValue())))
        .collect(Collectors.toSet());
  }
}
