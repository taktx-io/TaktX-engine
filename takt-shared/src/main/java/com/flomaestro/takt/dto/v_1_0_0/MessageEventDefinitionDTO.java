package com.flomaestro.takt.dto.v_1_0_0;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class MessageEventDefinitionDTO extends EventDefinitionDTO {

  @JsonProperty("r")
  private String messageRef;

  public MessageEventDefinitionDTO(String id, String messageRef) {
    super(id, Constants.NONE);
    this.messageRef = messageRef;
  }
}
