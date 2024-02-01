package nl.qunit.bpmnmeister.pi;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;
import lombok.Getter;
import nl.qunit.bpmnmeister.pd.model.ProcessDefinitionKey;

@Getter
public class ProcessInstanceStartCommand {
  private final ProcessDefinitionKey processDefinitionKey;
  private final String elementId;
  private final Map<String, Object> variables;

  @JsonCreator
  public ProcessInstanceStartCommand(
      @JsonProperty("processDefinitionKey") ProcessDefinitionKey processDefinitionKey,
      @JsonProperty("elementId") String elementId,
      @JsonProperty("variables") Map<String, Object> variables) {
    this.processDefinitionKey = processDefinitionKey;
    this.elementId = elementId;
    this.variables = variables;
  }
}
