package io.taktx.client.serdes;

import io.taktx.dto.ProcessDefinitionKey;

public class ProcessDefinitionKeyJsonDeserializer extends JsonDeserializer<ProcessDefinitionKey> {

  public ProcessDefinitionKeyJsonDeserializer() {
    super(ProcessDefinitionKey.class);
  }
}
