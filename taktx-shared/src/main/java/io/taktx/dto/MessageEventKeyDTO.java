package io.taktx.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode
public class MessageEventKeyDTO {

  @JsonProperty("msg")
  private String messageName;

  public MessageEventKeyDTO(String messageName) {
    this.messageName = messageName;
  }
}
