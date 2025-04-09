package io.taktx.engine.pi.testengine;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.cbor.CBORFactory;
import io.quarkus.kafka.client.serialization.ObjectMapperDeserializer;
import io.taktx.dto.MessageEventDTO;

public class MessageEventDeserializer extends ObjectMapperDeserializer<MessageEventDTO> {

  public MessageEventDeserializer() {
    super(MessageEventDTO.class, new ObjectMapper(new CBORFactory()));
  }
}
