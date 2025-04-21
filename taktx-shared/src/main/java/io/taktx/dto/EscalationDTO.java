package io.taktx.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@EqualsAndHashCode
@NoArgsConstructor
@JsonFormat(shape = JsonFormat.Shape.ARRAY)
public class EscalationDTO {
  private String id;

  private String name;

  private String code;

  public EscalationDTO(String id, String name, String code) {
    this.id = id;
    this.name = name;
    this.code = code;
  }
}
