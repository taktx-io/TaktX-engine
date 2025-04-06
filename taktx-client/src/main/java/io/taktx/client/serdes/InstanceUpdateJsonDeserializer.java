package io.taktx.client.serdes;

import io.taktx.dto.v_1_0_0.InstanceUpdateDTO;

public class InstanceUpdateJsonDeserializer extends JsonDeserializer<InstanceUpdateDTO> {
  public InstanceUpdateJsonDeserializer() {
    super(InstanceUpdateDTO.class);
  }
}
