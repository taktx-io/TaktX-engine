package com.flomaestro.client.serdes;

import com.flomaestro.takt.dto.v_1_0_0.MessageEventKeyDTO;

public class MessageEventKeySerializer extends JsonSerializer<MessageEventKeyDTO> {

  public MessageEventKeySerializer() {
    super(MessageEventKeyDTO.class);
  }
}
