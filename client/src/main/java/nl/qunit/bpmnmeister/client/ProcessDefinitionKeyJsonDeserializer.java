package nl.qunit.bpmnmeister.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import nl.qunit.bpmnmeister.pd.model.ProcessDefinitionKey;

public class ProcessDefinitionKeyJsonDeserializer extends JsonDeserializer<ProcessDefinitionKey> {

  private static final ObjectMapper objectMapper = new ObjectMapper();

  public ProcessDefinitionKeyJsonDeserializer() {
    super(ProcessDefinitionKey.class, objectMapper);
  }
}
