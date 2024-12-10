package nl.qunit.bpmnmeister.client;

import nl.qunit.bpmnmeister.pd.model.v_1_0_0.ProcessDefinitionDTO;

public class ProcessDefinitionJsonDeserializer extends JsonDeserializer<ProcessDefinitionDTO> {

  public ProcessDefinitionJsonDeserializer() {
    super(ProcessDefinitionDTO.class);
  }
}
