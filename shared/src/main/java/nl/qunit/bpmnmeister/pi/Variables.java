package nl.qunit.bpmnmeister.pi;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
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
