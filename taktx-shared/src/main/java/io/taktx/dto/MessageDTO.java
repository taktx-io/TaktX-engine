package io.taktx.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@EqualsAndHashCode
@NoArgsConstructor
@JsonFormat(shape = JsonFormat.Shape.ARRAY)
public class MessageDTO {
  private String id;

  private String name;

  private String correlationKey;

  public MessageDTO(String id, String name, String correlationKey) {
    this.id = id;
    this.name = name;
    this.correlationKey = correlationKey;
  }
}
