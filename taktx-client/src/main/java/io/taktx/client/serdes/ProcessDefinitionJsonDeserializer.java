package io.taktx.client.serdes;

import io.taktx.dto.v_1_0_0.ProcessDefinitionDTO;

public class ProcessDefinitionJsonDeserializer extends JsonDeserializer<ProcessDefinitionDTO> {

  public ProcessDefinitionJsonDeserializer() {
    super(ProcessDefinitionDTO.class);
  }
}
