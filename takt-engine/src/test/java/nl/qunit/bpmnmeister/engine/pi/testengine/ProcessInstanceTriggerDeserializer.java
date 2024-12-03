package nl.qunit.bpmnmeister.engine.pi.testengine;

import io.quarkus.kafka.client.serialization.ObjectMapperDeserializer;
import nl.qunit.bpmnmeister.pi.ProcessInstanceTrigger;

public class ProcessInstanceTriggerDeserializer extends ObjectMapperDeserializer<ProcessInstanceTrigger> {

  public ProcessInstanceTriggerDeserializer() {
    super(ProcessInstanceTrigger.class);
  }
}
