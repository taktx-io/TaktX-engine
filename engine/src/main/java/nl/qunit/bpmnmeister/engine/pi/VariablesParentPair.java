package nl.qunit.bpmnmeister.engine.pi;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.UUID;
import lombok.Getter;
import nl.qunit.bpmnmeister.pi.VariablesDTO;

@Getter
public class VariablesParentPair {

  private final VariablesDTO variables;
  private final UUID parent;

  @JsonCreator
  public VariablesParentPair(
      @JsonProperty("parent") UUID parent, @JsonProperty("variables") VariablesDTO variables) {
    this.variables = variables;
    this.parent = parent;
  }
}
