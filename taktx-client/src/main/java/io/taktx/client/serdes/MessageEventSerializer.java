package io.taktx.client.serdes;

import io.taktx.dto.MessageEventDTO;

public class MessageEventSerializer extends JsonSerializer<MessageEventDTO> {

  public MessageEventSerializer() {
    super(MessageEventDTO.class);
  }
}
