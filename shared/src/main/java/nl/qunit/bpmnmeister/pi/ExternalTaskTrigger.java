package nl.qunit.bpmnmeister.pi;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;
import lombok.Getter;
import nl.qunit.bpmnmeister.pd.model.BaseElementId;
import nl.qunit.bpmnmeister.pd.model.ProcessDefinitionKey;

@Getter
public class ExternalTaskTrigger {
  private final ProcessInstanceKey processInstanceKey;
  private final ProcessDefinitionKey processDefinitionKey;
  private final BaseElementId externalTaskId;
  private final Map<String, JsonNode> variables;

  @JsonCreator
  public ExternalTaskTrigger(
      @JsonProperty("processInstanceKey") ProcessInstanceKey processInstanceKey,
      @JsonProperty("processDefinitionKey") ProcessDefinitionKey processDefinitionKey,
      @JsonProperty("externalTaskId") BaseElementId externalTaskId,
      @JsonProperty("variables") Map<String, JsonNode> variables) {
    this.processInstanceKey = processInstanceKey;
    this.processDefinitionKey = processDefinitionKey;
    this.externalTaskId = externalTaskId;
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
        + externalTaskId
        + '\''
        + ", variables="
        + variables
        + '}';
  }
}
