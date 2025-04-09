package io.taktx.client.serdes;

import io.taktx.dto.ProcessDefinitionDTO;

public class ProcessDefinitionJsonDeserializer extends JsonDeserializer<ProcessDefinitionDTO> {

  public ProcessDefinitionJsonDeserializer() {
    super(ProcessDefinitionDTO.class);
  }
}
