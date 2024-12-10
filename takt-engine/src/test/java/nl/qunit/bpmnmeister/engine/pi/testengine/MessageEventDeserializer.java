package nl.qunit.bpmnmeister.engine.pi.testengine;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.cbor.CBORFactory;
import io.quarkus.kafka.client.serialization.ObjectMapperDeserializer;
import nl.qunit.bpmnmeister.pi.state.MessageEventDTO;

public class MessageEventDeserializer extends ObjectMapperDeserializer<MessageEventDTO> {

  public MessageEventDeserializer() {
    super(MessageEventDTO.class, new ObjectMapper(new CBORFactory()));
  }
}
