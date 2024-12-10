package nl.qunit.bpmnmeister.client;

import nl.qunit.bpmnmeister.pi.trigger.v_1_0_0.ExternalTaskResponseTriggerDTO;

public class ExternalTaskTriggerResponseSerializer
    extends JsonSerializer<ExternalTaskResponseTriggerDTO> {

  public ExternalTaskTriggerResponseSerializer() {
    super(ExternalTaskResponseTriggerDTO.class);
  }
}
