package io.taktx.client.serdes;

import io.taktx.dto.ExternalTaskTriggerDTO;

public class ExternalTaskTriggerJsonDeserializer extends JsonDeserializer<ExternalTaskTriggerDTO> {
  public ExternalTaskTriggerJsonDeserializer() {
    super(ExternalTaskTriggerDTO.class);
  }
}
