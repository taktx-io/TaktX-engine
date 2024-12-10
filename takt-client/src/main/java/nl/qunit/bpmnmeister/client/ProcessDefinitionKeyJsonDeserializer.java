package nl.qunit.bpmnmeister.client;

import nl.qunit.bpmnmeister.pd.model.v_1_0_0.ProcessDefinitionKey;

public class ProcessDefinitionKeyJsonDeserializer extends JsonDeserializer<ProcessDefinitionKey> {

  public ProcessDefinitionKeyJsonDeserializer() {
    super(ProcessDefinitionKey.class);
  }
}
