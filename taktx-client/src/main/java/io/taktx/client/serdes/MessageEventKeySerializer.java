package io.taktx.client.serdes;

import io.taktx.dto.v_1_0_0.MessageEventKeyDTO;

public class MessageEventKeySerializer extends JsonSerializer<MessageEventKeyDTO> {

  public MessageEventKeySerializer() {
    super(MessageEventKeyDTO.class);
  }
}
