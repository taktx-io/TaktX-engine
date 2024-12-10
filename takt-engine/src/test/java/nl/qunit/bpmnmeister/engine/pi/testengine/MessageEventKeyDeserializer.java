package nl.qunit.bpmnmeister.engine.pi.testengine;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.cbor.CBORFactory;
import io.quarkus.kafka.client.serialization.ObjectMapperDeserializer;
import nl.qunit.bpmnmeister.pi.state.MessageEventKeyDTO;

public class MessageEventKeyDeserializer extends ObjectMapperDeserializer<MessageEventKeyDTO> {

  public MessageEventKeyDeserializer() {
    super(MessageEventKeyDTO.class, new ObjectMapper(new CBORFactory()));
  }
}
