package com.flomaestro.client.serdes;

import com.flomaestro.takt.dto.v_1_0_0.StartCommandDTO;

public class StartCommandSerializer extends JsonSerializer<StartCommandDTO> {

  public StartCommandSerializer() {
    super(StartCommandDTO.class);
  }
}
