package nl.qunit.bpmnmeister.engine.pi;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.UUID;
import lombok.Getter;
import nl.qunit.bpmnmeister.pi.Variables;

@Getter
public class VariablesParentPair {

  private final Variables variables;
  private final UUID parent;

  @JsonCreator
  public VariablesParentPair(
      @JsonProperty("parent") UUID parent, @JsonProperty("variables") Variables variables) {
    this.variables = variables;
    this.parent = parent;
  }
}
