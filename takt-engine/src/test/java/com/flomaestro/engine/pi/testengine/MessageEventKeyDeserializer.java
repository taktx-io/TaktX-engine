package com.flomaestro.engine.pi.testengine;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.cbor.CBORFactory;
import com.flomaestro.takt.dto.v_1_0_0.MessageEventKeyDTO;
import io.quarkus.kafka.client.serialization.ObjectMapperDeserializer;

public class MessageEventKeyDeserializer extends ObjectMapperDeserializer<MessageEventKeyDTO> {

  public MessageEventKeyDeserializer() {
    super(MessageEventKeyDTO.class, new ObjectMapper(new CBORFactory()));
  }
}
