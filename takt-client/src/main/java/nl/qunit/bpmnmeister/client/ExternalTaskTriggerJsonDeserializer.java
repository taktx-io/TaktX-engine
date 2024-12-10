package nl.qunit.bpmnmeister.client;

import nl.qunit.bpmnmeister.pi.trigger.v_1_0_0.ExternalTaskTriggerDTO;

public class ExternalTaskTriggerJsonDeserializer extends JsonDeserializer<ExternalTaskTriggerDTO> {
  public ExternalTaskTriggerJsonDeserializer() {
    super(ExternalTaskTriggerDTO.class);
  }
}
