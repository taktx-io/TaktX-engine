package nl.qunit.bpmnmeister.engine.pi.testengine;

import io.quarkus.kafka.client.serialization.ObjectMapperDeserializer;
import nl.qunit.bpmnmeister.pi.InstanceUpdate;

public class InstanceUpdateDeserializer extends ObjectMapperDeserializer<InstanceUpdate> {

  public InstanceUpdateDeserializer() {
    super(InstanceUpdate.class);
  }
}
