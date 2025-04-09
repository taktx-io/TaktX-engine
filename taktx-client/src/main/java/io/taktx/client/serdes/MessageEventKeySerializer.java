package io.taktx.client.serdes;

import io.taktx.dto.MessageEventKeyDTO;

public class MessageEventKeySerializer extends JsonSerializer<MessageEventKeyDTO> {

  public MessageEventKeySerializer() {
    super(MessageEventKeyDTO.class);
  }
}
