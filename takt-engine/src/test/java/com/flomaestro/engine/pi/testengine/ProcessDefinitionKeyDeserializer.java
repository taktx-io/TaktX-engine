package com.flomaestro.engine.pi.testengine;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.cbor.CBORFactory;
import com.flomaestro.takt.dto.v_1_0_0.ProcessDefinitionKey;
import io.quarkus.kafka.client.serialization.ObjectMapperDeserializer;

public class ProcessDefinitionKeyDeserializer
    extends ObjectMapperDeserializer<ProcessDefinitionKey> {

  public ProcessDefinitionKeyDeserializer() {
    super(ProcessDefinitionKey.class, new ObjectMapper(new CBORFactory()));
  }
}
