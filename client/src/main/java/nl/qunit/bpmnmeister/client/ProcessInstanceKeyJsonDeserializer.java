package nl.qunit.bpmnmeister.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import nl.qunit.bpmnmeister.pi.ProcessInstanceKey;

public class ProcessInstanceKeyJsonDeserializer extends JsonDeserializer<ProcessInstanceKey> {

  private static final ObjectMapper objectMapper = new ObjectMapper();

  public ProcessInstanceKeyJsonDeserializer() {
    super(ProcessInstanceKey.class, objectMapper);
  }
}
