package nl.qunit.bpmnmeister.client;

import nl.qunit.bpmnmeister.pd.model.ProcessDefinitionDTO;

public class ProcessDefinitionJsonDeserializer extends JsonDeserializer<ProcessDefinitionDTO> {

  public ProcessDefinitionJsonDeserializer() {
    super(ProcessDefinitionDTO.class);
  }
}
