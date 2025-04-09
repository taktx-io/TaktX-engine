package io.taktx.engine.pi.testengine;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.cbor.CBORFactory;
import io.quarkus.kafka.client.serialization.ObjectMapperDeserializer;
import io.taktx.dto.ProcessInstanceTriggerDTO;

public class ProcessInstanceTriggerDeserializer
    extends ObjectMapperDeserializer<ProcessInstanceTriggerDTO> {

  public ProcessInstanceTriggerDeserializer() {
    super(ProcessInstanceTriggerDTO.class, new ObjectMapper(new CBORFactory()));
  }
}
