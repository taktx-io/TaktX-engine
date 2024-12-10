package nl.qunit.bpmnmeister.client;

import nl.qunit.bpmnmeister.pi.ExternalTaskResponseTriggerDTO;

public class ExternalTaskTriggerResponseSerializer
    extends JsonSerializer<ExternalTaskResponseTriggerDTO> {

  public ExternalTaskTriggerResponseSerializer() {
    super(ExternalTaskResponseTriggerDTO.class);
  }
}
