package nl.qunit.bpmnmeister.pi;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

@Getter
public class ProcessInstanceStartCommand {
  private final String processDefinitionId;
  private final String elementId;
  private final Variables variables;

  @JsonCreator
  public ProcessInstanceStartCommand(
      @JsonProperty("processDefinitionId") String processDefinitionId,
      @JsonProperty("elementId") String elementId,
      @JsonProperty("variables") Variables variables) {
    this.processDefinitionId = processDefinitionId;
    this.elementId = elementId;
    this.variables = variables;
  }
}
