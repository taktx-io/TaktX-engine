package com.flomaestro.client.serdes;

import com.flomaestro.takt.dto.v_1_0_0.ExternalTaskTriggerDTO;

public class ExternalTaskTriggerJsonDeserializer extends JsonDeserializer<ExternalTaskTriggerDTO> {
  public ExternalTaskTriggerJsonDeserializer() {
    super(ExternalTaskTriggerDTO.class);
  }
}
