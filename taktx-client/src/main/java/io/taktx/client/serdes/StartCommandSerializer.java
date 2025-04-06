package io.taktx.client.serdes;

import io.taktx.dto.v_1_0_0.StartCommandDTO;

public class StartCommandSerializer extends JsonSerializer<StartCommandDTO> {

  public StartCommandSerializer() {
    super(StartCommandDTO.class);
  }
}
