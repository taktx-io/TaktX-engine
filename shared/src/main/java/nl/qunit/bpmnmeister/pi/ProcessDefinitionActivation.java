package nl.qunit.bpmnmeister.pi;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import nl.qunit.bpmnmeister.pd.model.ProcessDefinition;
import nl.qunit.bpmnmeister.pd.model.ProcessDefinitionStateEnum;

@Getter
public class ProcessDefinitionActivation {
  ProcessDefinition processDefinition;
  ProcessDefinitionStateEnum state;

  @JsonCreator
  public ProcessDefinitionActivation(
      @JsonProperty("processDefinition") ProcessDefinition processDefinition,
      @JsonProperty("newState") ProcessDefinitionStateEnum state) {
    this.state = state;
    this.processDefinition = processDefinition;
  }
}
