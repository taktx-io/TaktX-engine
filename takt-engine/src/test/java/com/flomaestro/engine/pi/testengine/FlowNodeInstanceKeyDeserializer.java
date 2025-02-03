package com.flomaestro.engine.pi.testengine;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.cbor.CBORFactory;
import com.flomaestro.takt.dto.v_1_0_0.FlowNodeInstanceKeyDTO;
import io.quarkus.kafka.client.serialization.ObjectMapperDeserializer;

public class FlowNodeInstanceKeyDeserializer
    extends ObjectMapperDeserializer<FlowNodeInstanceKeyDTO> {

  public FlowNodeInstanceKeyDeserializer() {
    super(FlowNodeInstanceKeyDTO.class, new ObjectMapper(new CBORFactory()));
  }
}
