package com.flomaestro.client;

import com.flomaestro.takt.dto.v_1_0_0.ProcessInstanceTriggerDTO;

public class ProcessInstanceTriggerSerializer extends JsonSerializer<ProcessInstanceTriggerDTO> {

  public ProcessInstanceTriggerSerializer() {
    super(ProcessInstanceTriggerDTO.class);
  }
}
