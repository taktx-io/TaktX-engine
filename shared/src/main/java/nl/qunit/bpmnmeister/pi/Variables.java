package nl.qunit.bpmnmeister.pi;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Nonnull;
import java.util.HashMap;
import java.util.Map;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

@Getter
@ToString
@EqualsAndHashCode
public class Variables {

  private final Map<String, JsonNode> variables;

  @JsonCreator
  public Variables(
      @Nonnull @JsonProperty("variables") Map<String, JsonNode> variables
  ) {
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

  @JsonIgnore
  public JsonNode get(String key) {
    return variables.get(key);
  }

  @JsonIgnore
  public JsonNode put(String key, JsonNode value) {
    return variables.put(key, value);
  }

  @JsonIgnore
  public JsonNode remove(String key) {
    return variables.remove(key);
  }

  @JsonIgnore
  public void merge(Variables variablesToMerge) {
    variables.putAll(variablesToMerge.variables);
  }

}
