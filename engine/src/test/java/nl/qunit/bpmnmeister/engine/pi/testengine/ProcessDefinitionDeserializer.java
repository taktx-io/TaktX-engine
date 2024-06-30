package nl.qunit.bpmnmeister.engine.pi.testengine;

import io.quarkus.kafka.client.serialization.ObjectMapperDeserializer;
import nl.qunit.bpmnmeister.pd.model.ProcessDefinition;

public class ProcessDefinitionDeserializer extends ObjectMapperDeserializer<ProcessDefinition> {

  public ProcessDefinitionDeserializer() {
    super(ProcessDefinition.class);
  }
}
