package com.flomaestro.takt.dto.v_1_0_0;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.cbor.CBORFactory;
import java.util.Map;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
@NoArgsConstructor
@EqualsAndHashCode
public class VariablesDTO {

  public static final ObjectMapper OBJECT_MAPPER = new ObjectMapper(new CBORFactory());

  @JsonProperty("v")
  private Map<String, JsonNode> variables;

  public VariablesDTO(Map<String, JsonNode> variables) {
    this.variables = variables;
  }

  public static VariablesDTO empty() {
    return new VariablesDTO(Map.of());
  }

  public static VariablesDTO of(String key, Object value) {
    return new VariablesDTO(Map.of(key, OBJECT_MAPPER.valueToTree(value)));
  }

  public static VariablesDTO of(Map<String, JsonNode> variables) {
    return new VariablesDTO(variables);
  }

  public static VariablesDTO of(String key, Object value, String key2, Object value2) {
    return new VariablesDTO(
        Map.of(key, OBJECT_MAPPER.valueToTree(value), key2, OBJECT_MAPPER.valueToTree(value2)));
  }

  public static VariablesDTO of(
      String key, Object value, String key2, Object value2, String key3, Object value3) {
    JsonNode v1 = OBJECT_MAPPER.valueToTree(value);
    JsonNode v2 = OBJECT_MAPPER.valueToTree(value2);
    JsonNode v3 = OBJECT_MAPPER.valueToTree(value3);
    return new VariablesDTO(Map.of(key, v1, key2, v2, key3, v3));
  }

  @JsonIgnore
  public JsonNode get(String key) {
    return variables.get(key);
  }
}
