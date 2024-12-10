package nl.qunit.bpmnmeister.pi.trigger.v_1_0_0;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import nl.qunit.bpmnmeister.pd.model.v_1_0_0.ProcessDefinitionDTO;
import nl.qunit.bpmnmeister.pd.model.v_1_0_0.ProcessDefinitionStateEnum;

@Getter
public class ProcessDefinitionActivationDTO {
  ProcessDefinitionDTO processDefinition;
  ProcessDefinitionStateEnum state;

  @JsonCreator
  public ProcessDefinitionActivationDTO(
      @JsonProperty("processDefinition") ProcessDefinitionDTO processDefinition,
      @JsonProperty("newState") ProcessDefinitionStateEnum state) {
    this.state = state;
    this.processDefinition = processDefinition;
  }
}
