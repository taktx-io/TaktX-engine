package nl.qunit.bpmnmeister.engine.pi.testengine;

import io.quarkus.kafka.client.serialization.ObjectMapperDeserializer;
import nl.qunit.bpmnmeister.pi.ExternalTaskTrigger;

public class ExternalTaskTriggerDeserializer extends ObjectMapperDeserializer<ExternalTaskTrigger> {

  public ExternalTaskTriggerDeserializer() {
    super(ExternalTaskTrigger.class);
  }
}
