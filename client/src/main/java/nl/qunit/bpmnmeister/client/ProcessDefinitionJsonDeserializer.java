package nl.qunit.bpmnmeister.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import nl.qunit.bpmnmeister.pd.model.ProcessDefinitionDTO;

public class ProcessDefinitionJsonDeserializer extends JsonDeserializer<ProcessDefinitionDTO> {

  private static final ObjectMapper objectMapper = new ObjectMapper();

  public ProcessDefinitionJsonDeserializer() {
    super(ProcessDefinitionDTO.class, objectMapper);
  }
}
