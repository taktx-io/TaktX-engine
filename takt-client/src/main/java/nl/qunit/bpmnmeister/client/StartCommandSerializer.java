package nl.qunit.bpmnmeister.client;

import nl.qunit.bpmnmeister.pi.StartCommandDTO;

public class StartCommandSerializer extends JsonSerializer<StartCommandDTO> {

  public StartCommandSerializer() {
    super(StartCommandDTO.class);
  }
}
