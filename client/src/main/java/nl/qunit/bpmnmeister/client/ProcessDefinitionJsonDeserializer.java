package nl.qunit.bpmnmeister.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import nl.qunit.bpmnmeister.pd.model.ProcessDefinition;

public class ProcessDefinitionJsonDeserializer extends JsonDeserializer<ProcessDefinition> {

  private static final ObjectMapper objectMapper = new ObjectMapper();

  public ProcessDefinitionJsonDeserializer() {
    super(ProcessDefinition.class, objectMapper);
  }
}
