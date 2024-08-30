package nl.qunit.bpmnmeister.engine.pi.testengine;

import io.quarkus.kafka.client.serialization.ObjectMapperDeserializer;
import nl.qunit.bpmnmeister.pi.ProcessInstanceTrigger2;

public class ProcessInstanceTriggerDeserializer extends ObjectMapperDeserializer<ProcessInstanceTrigger2> {

  public ProcessInstanceTriggerDeserializer() {
    super(ProcessInstanceTrigger2.class);
  }
}
