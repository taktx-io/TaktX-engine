package com.flomaestro.client.serdes;

import com.flomaestro.takt.dto.v_1_0_0.ProcessDefinitionDTO;

public class ProcessDefinitionJsonDeserializer extends JsonDeserializer<ProcessDefinitionDTO> {

  public ProcessDefinitionJsonDeserializer() {
    super(ProcessDefinitionDTO.class);
  }
}
