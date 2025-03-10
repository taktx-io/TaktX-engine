package com.flomaestro.client.serdes;

import com.flomaestro.takt.dto.v_1_0_0.ProcessDefinitionKey;

public class ProcessDefinitionKeyJsonDeserializer extends JsonDeserializer<ProcessDefinitionKey> {

  public ProcessDefinitionKeyJsonDeserializer() {
    super(ProcessDefinitionKey.class);
  }
}
