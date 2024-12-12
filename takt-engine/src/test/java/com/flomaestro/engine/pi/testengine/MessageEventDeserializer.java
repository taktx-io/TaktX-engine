package com.flomaestro.engine.pi.testengine;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.cbor.CBORFactory;
import com.flomaestro.takt.dto.v_1_0_0.MessageEventDTO;
import io.quarkus.kafka.client.serialization.ObjectMapperDeserializer;

public class MessageEventDeserializer extends ObjectMapperDeserializer<MessageEventDTO> {

  public MessageEventDeserializer() {
    super(MessageEventDTO.class, new ObjectMapper(new CBORFactory()));
  }
}
