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
public class EscalationSubscriptionDTO {
  private String name;

  private String code;

  public EscalationSubscriptionDTO(String name, String code) {
    this.name = name;
    this.code = code;
  }
}
