package com.flomaestro.engine.pi.testengine;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.cbor.CBORFactory;
import com.flomaestro.takt.dto.v_1_0_0.ProcessDefinitionDTO;
import io.quarkus.kafka.client.serialization.ObjectMapperDeserializer;

public class ProcessDefinitionDeserializer extends ObjectMapperDeserializer<ProcessDefinitionDTO> {

  public ProcessDefinitionDeserializer() {
    super(ProcessDefinitionDTO.class, new ObjectMapper(new CBORFactory()));
  }
}
