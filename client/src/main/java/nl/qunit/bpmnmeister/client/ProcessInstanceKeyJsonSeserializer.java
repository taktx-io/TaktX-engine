package nl.qunit.bpmnmeister.client;

import java.util.UUID;

public class ProcessInstanceKeyJsonSeserializer extends JsonSerializer<UUID> {

  public ProcessInstanceKeyJsonSeserializer() {
    super(UUID.class);
  }
}
