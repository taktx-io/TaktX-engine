package nl.qunit.bpmnmeister.engine.pi.testengine;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.cbor.CBORFactory;
import io.quarkus.kafka.client.serialization.ObjectMapperDeserializer;
import nl.qunit.bpmnmeister.pd.model.ProcessDefinitionKey;

public class ProcessDefinitionKeyDeserializer
    extends ObjectMapperDeserializer<ProcessDefinitionKey> {

  public ProcessDefinitionKeyDeserializer() {
    super(ProcessDefinitionKey.class, new ObjectMapper(new CBORFactory()));
  }
}
