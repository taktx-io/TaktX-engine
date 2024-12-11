package nl.qunit.bpmnmeister.pi.trigger.v_1_0_0;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import nl.qunit.bpmnmeister.pd.model.v_1_0_0.DefinitionsTriggerDTO;
import nl.qunit.bpmnmeister.pd.model.v_1_0_0.ProcessDefinitionKey;
import nl.qunit.bpmnmeister.pd.model.v_1_0_0.ProcessDefinitionStateEnum;

@Getter
public class ProcessDefinitionActivationDTO extends DefinitionsTriggerDTO {
  ProcessDefinitionKey processDefinitionKey;
  ProcessDefinitionStateEnum state;

  @JsonCreator
  public ProcessDefinitionActivationDTO(
      @JsonProperty("processDefinitionKey") ProcessDefinitionKey processDefinitionKey,
      @JsonProperty("newState") ProcessDefinitionStateEnum state) {
    this.state = state;
    this.processDefinitionKey = processDefinitionKey;
  }
}
