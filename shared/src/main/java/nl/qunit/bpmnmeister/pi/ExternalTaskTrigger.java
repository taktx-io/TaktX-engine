package nl.qunit.bpmnmeister.pi;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import nl.qunit.bpmnmeister.pd.model.ProcessDefinitionKey;

@Getter
public class ExternalTaskTrigger {
  private final ProcessInstanceKey processInstanceKey;
  private final ProcessDefinitionKey processDefinitionKey;
  private final String elementId;
  private final Variables variables;

  @JsonCreator
  public ExternalTaskTrigger(
      @JsonProperty("processInstanceKey") ProcessInstanceKey processInstanceKey,
      @JsonProperty("processDefinitionKey") ProcessDefinitionKey processDefinitionKey,
      @JsonProperty("externalTaskId") String elementId,
      @JsonProperty("variables") Variables variables) {
    this.processInstanceKey = processInstanceKey;
    this.processDefinitionKey = processDefinitionKey;
    this.elementId = elementId;
    this.variables = variables;
  }

  @Override
  public String toString() {
    return "ExternalTaskTrigger{"
        + "processInstanceKey="
        + processInstanceKey
        + ", processDefinitionKey="
        + processDefinitionKey
        + ", externalTaskId='"
        + elementId
        + '\''
        + ", variables="
        + variables
        + '}';
  }
}
