package nl.qunit.bpmnmeister.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import nl.qunit.bpmnmeister.pi.ExternalTaskTrigger;

public class ExternalTaskTriggerJsonDeserializer extends JsonDeserializer<ExternalTaskTrigger> {

  private static final ObjectMapper objectmapper = new ObjectMapper();

  public ExternalTaskTriggerJsonDeserializer() {
    super(ExternalTaskTrigger.class, objectmapper);
  }
}
