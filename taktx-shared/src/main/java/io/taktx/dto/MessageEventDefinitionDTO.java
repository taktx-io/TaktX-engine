package io.taktx.dto;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class MessageEventDefinitionDTO extends EventDefinitionDTO {

  private String messageRef;

  public MessageEventDefinitionDTO(String id, String messageRef) {
    super(id, null);
    this.messageRef = messageRef;
  }
}
