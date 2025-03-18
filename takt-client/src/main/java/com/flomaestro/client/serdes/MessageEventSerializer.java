package com.flomaestro.client.serdes;

import com.flomaestro.takt.dto.v_1_0_0.MessageEventDTO;

public class MessageEventSerializer extends JsonSerializer<MessageEventDTO> {

  public MessageEventSerializer() {
    super(MessageEventDTO.class);
  }
}
