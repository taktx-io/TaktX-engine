package com.flomaestro.client;

import com.flomaestro.takt.dto.v_1_0_0.ExternalTaskResponseTriggerDTO;

public class ExternalTaskTriggerResponseSerializer
    extends JsonSerializer<ExternalTaskResponseTriggerDTO> {

  public ExternalTaskTriggerResponseSerializer() {
    super(ExternalTaskResponseTriggerDTO.class);
  }
}
