package io.taktx.engine.pi.testengine;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.cbor.CBORFactory;
import io.quarkus.kafka.client.serialization.ObjectMapperDeserializer;
import io.taktx.dto.ProcessDefinitionKey;

public class ProcessDefinitionKeyDeserializer
    extends ObjectMapperDeserializer<ProcessDefinitionKey> {

  public ProcessDefinitionKeyDeserializer() {
    super(ProcessDefinitionKey.class, new ObjectMapper(new CBORFactory()));
  }
}
