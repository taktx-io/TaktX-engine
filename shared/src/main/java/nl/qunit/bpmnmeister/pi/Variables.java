package nl.qunit.bpmnmeister.pi;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Nonnull;
import java.util.HashMap;
import java.util.Map;
import lombok.Getter;

@Getter
public class Variables {

  public static final Variables EMPTY = new Variables(java.util.Map.of());
  private final Map<String, JsonNode> variables;

  @JsonCreator
  public Variables(@Nonnull @JsonProperty("variables") Map<String, JsonNode> variables) {
    this.variables = variables;
  }

  public static Variables of(String key, Object value) {
    return new Variables(Map.of(key, new ObjectMapper().valueToTree(value)));
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

  public Variables put(String key, JsonNode value) {
    Map<String, JsonNode> variables = new HashMap<>(this.variables);
    variables.put(key, value);
    return new Variables(Map.copyOf(variables));
  }

  public Variables remove(String key) {
    Map<String, JsonNode> variables = new HashMap<>(this.variables);
    variables.remove(key);
    return new Variables(Map.copyOf(variables));
  }

  public Variables merge(Variables variablesToMerge) {
    Map<String, JsonNode> variables = new HashMap<>(this.variables);
    variables.putAll(variablesToMerge.variables);
    return new Variables(Map.copyOf(variables));
  }

  @Override
  public String toString() {
    return "Variables{" + "variables=" + variables + '}';
  }
}
