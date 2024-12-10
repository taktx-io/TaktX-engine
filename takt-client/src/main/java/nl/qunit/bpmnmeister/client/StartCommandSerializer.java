package nl.qunit.bpmnmeister.client;

import nl.qunit.bpmnmeister.pi.trigger.v_1_0_0.StartCommandDTO;

public class StartCommandSerializer extends JsonSerializer<StartCommandDTO> {

  public StartCommandSerializer() {
    super(StartCommandDTO.class);
  }
}
