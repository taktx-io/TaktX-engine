package io.taktx.client.serdes;

import io.taktx.dto.v_1_0_0.MessageEventDTO;

public class MessageEventSerializer extends JsonSerializer<MessageEventDTO> {

  public MessageEventSerializer() {
    super(MessageEventDTO.class);
  }
}
