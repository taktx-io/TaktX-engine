package nl.qunit.bpmnmeister.engine.pi;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import nl.qunit.bpmnmeister.pi.ProcessInstanceKey;
import nl.qunit.bpmnmeister.pi.Variables;

@Getter
public class VariablesParentPair {

  private final Variables variables;
  private final ProcessInstanceKey parent;

  @JsonCreator
  public VariablesParentPair(
      @JsonProperty("parent") ProcessInstanceKey parent,
      @JsonProperty("variables") Variables variables) {
    this.variables = variables;
    this.parent = parent;
  }
}
