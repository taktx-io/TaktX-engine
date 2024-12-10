package nl.qunit.bpmnmeister.client;

import nl.qunit.bpmnmeister.pi.ExternalTaskTriggerDTO;

public class ExternalTaskTriggerJsonDeserializer extends JsonDeserializer<ExternalTaskTriggerDTO> {
  public ExternalTaskTriggerJsonDeserializer() {
    super(ExternalTaskTriggerDTO.class);
  }
}
