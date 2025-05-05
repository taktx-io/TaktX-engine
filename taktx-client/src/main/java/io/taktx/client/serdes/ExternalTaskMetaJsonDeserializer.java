package io.taktx.client.serdes;

import io.taktx.dto.ExternalTaskMetaDTO;
import io.taktx.dto.ExternalTaskTriggerDTO;

public class ExternalTaskMetaJsonDeserializer extends JsonDeserializer<ExternalTaskMetaDTO> {
  public ExternalTaskMetaJsonDeserializer() {
    super(ExternalTaskMetaDTO.class);
  }
}
