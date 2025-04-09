package io.taktx.client.serdes;

import io.taktx.dto.ProcessInstanceTriggerDTO;

public class ProcessInstanceTriggerSerializer extends JsonSerializer<ProcessInstanceTriggerDTO> {

  public ProcessInstanceTriggerSerializer() {
    super(ProcessInstanceTriggerDTO.class);
  }
}
