package io.taktx.client.serdes;

import io.taktx.dto.v_1_0_0.ExternalTaskTriggerDTO;

public class ExternalTaskTriggerJsonDeserializer extends JsonDeserializer<ExternalTaskTriggerDTO> {
  public ExternalTaskTriggerJsonDeserializer() {
    super(ExternalTaskTriggerDTO.class);
  }
}
