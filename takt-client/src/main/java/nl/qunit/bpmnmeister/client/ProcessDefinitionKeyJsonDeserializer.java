package nl.qunit.bpmnmeister.client;

import nl.qunit.bpmnmeister.pd.model.ProcessDefinitionKey;

public class ProcessDefinitionKeyJsonDeserializer extends JsonDeserializer<ProcessDefinitionKey> {

  public ProcessDefinitionKeyJsonDeserializer() {
    super(ProcessDefinitionKey.class);
  }
}
