package nl.qunit.bpmnmeister.client;

import nl.qunit.bpmnmeister.pi.ExternalTaskTrigger;

public class ExternalTaskTriggerJsonDeserializer extends JsonDeserializer<ExternalTaskTrigger> {
  public ExternalTaskTriggerJsonDeserializer() {
    super(ExternalTaskTrigger.class);
  }
}
