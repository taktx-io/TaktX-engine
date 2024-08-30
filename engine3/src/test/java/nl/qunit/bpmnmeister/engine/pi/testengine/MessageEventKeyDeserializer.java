package nl.qunit.bpmnmeister.engine.pi.testengine;

import io.quarkus.kafka.client.serialization.ObjectMapperDeserializer;
import nl.qunit.bpmnmeister.pi.state.MessageEventKey;

public class MessageEventKeyDeserializer extends ObjectMapperDeserializer<MessageEventKey> {

  public MessageEventKeyDeserializer() {
    super(MessageEventKey.class);
  }
}
