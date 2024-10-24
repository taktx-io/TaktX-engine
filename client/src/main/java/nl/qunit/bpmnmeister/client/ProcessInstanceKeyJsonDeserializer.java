package nl.qunit.bpmnmeister.client;

import java.util.UUID;

public class ProcessInstanceKeyJsonDeserializer extends JsonDeserializer<UUID> {

  public ProcessInstanceKeyJsonDeserializer() {
    super(UUID.class);
  }
}
