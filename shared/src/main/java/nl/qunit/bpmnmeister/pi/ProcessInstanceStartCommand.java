package nl.qunit.bpmnmeister.pi;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import nl.qunit.bpmnmeister.pd.model.ProcessDefinitionKey;

@Getter
public class ProcessInstanceStartCommand {
  private final ProcessDefinitionKey processDefinitionKey;
  private final String elementId;

  @JsonCreator
  public ProcessInstanceStartCommand(
      @JsonProperty("processDefinitionKey") ProcessDefinitionKey processDefinitionKey,
      @JsonProperty("elementId") String elementId) {
    this.processDefinitionKey = processDefinitionKey;
    this.elementId = elementId;
  }
}
