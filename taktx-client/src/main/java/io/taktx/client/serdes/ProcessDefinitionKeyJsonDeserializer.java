package io.taktx.client.serdes;

import io.taktx.dto.v_1_0_0.ProcessDefinitionKey;

public class ProcessDefinitionKeyJsonDeserializer extends JsonDeserializer<ProcessDefinitionKey> {

  public ProcessDefinitionKeyJsonDeserializer() {
    super(ProcessDefinitionKey.class);
  }
}
