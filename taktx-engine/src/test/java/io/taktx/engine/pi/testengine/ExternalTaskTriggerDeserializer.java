package io.taktx.engine.pi.testengine;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.cbor.CBORFactory;
import io.quarkus.kafka.client.serialization.ObjectMapperDeserializer;
import io.taktx.dto.ExternalTaskTriggerDTO;

public class ExternalTaskTriggerDeserializer
    extends ObjectMapperDeserializer<ExternalTaskTriggerDTO> {

  public ExternalTaskTriggerDeserializer() {
    super(ExternalTaskTriggerDTO.class, new ObjectMapper(new CBORFactory()));
  }
}
