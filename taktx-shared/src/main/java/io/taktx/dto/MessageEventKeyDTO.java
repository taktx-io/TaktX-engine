package io.taktx.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode
@JsonFormat(shape = JsonFormat.Shape.ARRAY)
public class MessageEventKeyDTO {

  private String messageName;

  public MessageEventKeyDTO(String messageName) {
    this.messageName = messageName;
  }
}
