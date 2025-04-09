package io.taktx.engine.pi.testengine;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.cbor.CBORFactory;
import io.quarkus.kafka.client.serialization.ObjectMapperDeserializer;
import io.taktx.dto.InstanceUpdateDTO;

public class InstanceUpdateDeserializer extends ObjectMapperDeserializer<InstanceUpdateDTO> {

  public InstanceUpdateDeserializer() {
    super(InstanceUpdateDTO.class, new ObjectMapper(new CBORFactory()));
  }
}
