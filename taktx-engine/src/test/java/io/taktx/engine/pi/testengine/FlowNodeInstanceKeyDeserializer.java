package io.taktx.engine.pi.testengine;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.cbor.CBORFactory;
import io.quarkus.kafka.client.serialization.ObjectMapperDeserializer;
import io.taktx.dto.FlowNodeInstanceKeyDTO;

public class FlowNodeInstanceKeyDeserializer
    extends ObjectMapperDeserializer<FlowNodeInstanceKeyDTO> {

  public FlowNodeInstanceKeyDeserializer() {
    super(FlowNodeInstanceKeyDTO.class, new ObjectMapper(new CBORFactory()));
  }
}
