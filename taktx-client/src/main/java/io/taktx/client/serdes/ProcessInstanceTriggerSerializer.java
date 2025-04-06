package io.taktx.client.serdes;

import io.taktx.dto.v_1_0_0.ProcessInstanceTriggerDTO;

public class ProcessInstanceTriggerSerializer extends JsonSerializer<ProcessInstanceTriggerDTO> {

  public ProcessInstanceTriggerSerializer() {
    super(ProcessInstanceTriggerDTO.class);
  }
}
