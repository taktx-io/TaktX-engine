package io.taktx.client.serdes;

import io.taktx.dto.StartCommandDTO;

public class StartCommandSerializer extends JsonSerializer<StartCommandDTO> {

  public StartCommandSerializer() {
    super(StartCommandDTO.class);
  }
}
