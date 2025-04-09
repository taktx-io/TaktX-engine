package io.taktx.client.serdes;

import io.taktx.dto.InstanceUpdateDTO;

public class InstanceUpdateJsonDeserializer extends JsonDeserializer<InstanceUpdateDTO> {
  public InstanceUpdateJsonDeserializer() {
    super(InstanceUpdateDTO.class);
  }
}
