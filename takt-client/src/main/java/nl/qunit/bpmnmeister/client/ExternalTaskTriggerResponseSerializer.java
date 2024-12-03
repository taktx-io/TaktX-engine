package nl.qunit.bpmnmeister.client;

import nl.qunit.bpmnmeister.pi.ExternalTaskResponseTrigger;

public class ExternalTaskTriggerResponseSerializer
    extends JsonSerializer<ExternalTaskResponseTrigger> {

  public ExternalTaskTriggerResponseSerializer() {
    super(ExternalTaskResponseTrigger.class);
  }
}
