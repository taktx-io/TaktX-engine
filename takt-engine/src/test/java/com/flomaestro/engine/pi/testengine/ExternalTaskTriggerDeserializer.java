package com.flomaestro.engine.pi.testengine;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.cbor.CBORFactory;
import com.flomaestro.takt.dto.v_1_0_0.ExternalTaskTriggerDTO;
import io.quarkus.kafka.client.serialization.ObjectMapperDeserializer;

public class ExternalTaskTriggerDeserializer
    extends ObjectMapperDeserializer<ExternalTaskTriggerDTO> {

  public ExternalTaskTriggerDeserializer() {
    super(ExternalTaskTriggerDTO.class, new ObjectMapper(new CBORFactory()));
  }
}
