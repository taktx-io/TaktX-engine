package nl.qunit.bpmnmeister.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;

public class ProcessInstanceKeyJsonDeserializer extends JsonDeserializer<UUID> {

  private static final ObjectMapper objectMapper = new ObjectMapper();

  public ProcessInstanceKeyJsonDeserializer() {
    super(UUID.class, objectMapper);
  }
}
