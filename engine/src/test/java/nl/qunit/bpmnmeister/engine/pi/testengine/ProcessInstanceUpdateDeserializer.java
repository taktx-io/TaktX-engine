package nl.qunit.bpmnmeister.engine.pi.testengine;

import io.quarkus.kafka.client.serialization.ObjectMapperDeserializer;
import nl.qunit.bpmnmeister.pi.ProcessInstanceUpdate;

public class ProcessInstanceUpdateDeserializer extends ObjectMapperDeserializer<ProcessInstanceUpdate> {

  public ProcessInstanceUpdateDeserializer() {
    super(ProcessInstanceUpdate.class);
  }
}
