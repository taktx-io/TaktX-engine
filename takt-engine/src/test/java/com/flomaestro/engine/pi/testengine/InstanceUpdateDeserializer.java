package com.flomaestro.engine.pi.testengine;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.cbor.CBORFactory;
import com.flomaestro.takt.dto.v_1_0_0.InstanceUpdateDTO;
import io.quarkus.kafka.client.serialization.ObjectMapperDeserializer;

public class InstanceUpdateDeserializer extends ObjectMapperDeserializer<InstanceUpdateDTO> {

  public InstanceUpdateDeserializer() {
    super(InstanceUpdateDTO.class, new ObjectMapper(new CBORFactory()));
  }
}
