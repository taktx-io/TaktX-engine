package nl.qunit.bpmnmeister.engine.pi.testengine;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.cbor.CBORFactory;
import io.quarkus.kafka.client.serialization.ObjectMapperDeserializer;
import nl.qunit.bpmnmeister.pd.model.v_1_0_0.ProcessDefinitionKey;

public class ProcessDefinitionKeyDeserializer
    extends ObjectMapperDeserializer<ProcessDefinitionKey> {

  public ProcessDefinitionKeyDeserializer() {
    super(ProcessDefinitionKey.class, new ObjectMapper(new CBORFactory()));
  }
}
