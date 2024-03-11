package nl.qunit.bpmnmeister.pi;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;
import lombok.Getter;
import nl.qunit.bpmnmeister.pd.model.BaseElementId;
import nl.qunit.bpmnmeister.pd.model.ProcessDefinitionKey;

@Getter
public class ProcessInstanceStartCommand {
  private final ProcessDefinitionKey processDefinitionKey;
  private final BaseElementId elementId;
  private final Map<String, JsonNode> variables;

  @JsonCreator
  public ProcessInstanceStartCommand(
      @JsonProperty("processDefinitionKey") ProcessDefinitionKey processDefinitionKey,
      @JsonProperty("elementId") BaseElementId elementId,
      @JsonProperty("variables") Map<String, JsonNode> variables) {
    this.processDefinitionKey = processDefinitionKey;
    this.elementId = elementId;
    this.variables = variables;
  }
}
