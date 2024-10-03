package nl.qunit.bpmnmeister.pi;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

public class Variables {

  private final Map<String, JsonNode> variables;

  public Variables(Map<String, JsonNode> variables) {
    this.variables = new HashMap<>(variables);
  }

  public static Variables empty() {
    return new Variables(Map.of());
  }

  public static Variables of(String key, Object value) {
    return new Variables(Map.of(key, new ObjectMapper().valueToTree(value)));
  }

  public static Variables of(Map<String, JsonNode> variables) {
    return new Variables(variables);
  }

  public static Variables of(String key, Object value, String key2, Object value2) {
    return new Variables(
        Map.of(
            key,
            new ObjectMapper().valueToTree(value),
            key2,
            new ObjectMapper().valueToTree(value2)));
  }

  public static Variables of(
      String key, Object value, String key2, Object value2, String key3, Object value3) {
    JsonNode v1 = new ObjectMapper().valueToTree(value);
    JsonNode v2 = new ObjectMapper().valueToTree(value2);
    JsonNode v3 = new ObjectMapper().valueToTree(value3);
    return new Variables(Map.of(key, v1, key2, v2, key3, v3));
  }

  public JsonNode get(String key) {
    return variables.get(key);
  }

  public JsonNode put(String key, JsonNode value) {
    return variables.put(key, value);
  }

  public JsonNode remove(String key) {
    return variables.remove(key);
  }

  public void merge(Variables variablesToMerge) {
    variables.putAll(variablesToMerge.variables);
  }

  public int size() {
    return variables.size();
  }

  public void clear() {
    variables.clear();
  }

  public Set<String> keySet() {
    return variables.keySet();
  }

  public Collection<JsonNode> values() {
    return variables.values();
  }

  public Set<Entry<String, JsonNode>> entrySet() {
    return variables.entrySet();
  }
}
