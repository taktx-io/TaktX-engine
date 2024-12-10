package nl.qunit.bpmnmeister.engine.pi.testengine;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.cbor.CBORFactory;
import io.quarkus.kafka.client.serialization.ObjectMapperDeserializer;
import nl.qunit.bpmnmeister.pi.trigger.v_1_0_0.InstanceUpdateDTO;

public class InstanceUpdateDeserializer extends ObjectMapperDeserializer<InstanceUpdateDTO> {

  public InstanceUpdateDeserializer() {
    super(InstanceUpdateDTO.class, new ObjectMapper(new CBORFactory()));
  }
}
