package com.flomaestro.takt.dto.v_1_0_0;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@EqualsAndHashCode
@NoArgsConstructor
public class EscalationDTO {
  @JsonProperty("i")
  private String id;

  @JsonProperty("n")
  private String name;

  @JsonProperty("c")
  private String code;

  public EscalationDTO(String id, String name, String code) {
    this.id = id;
    this.name = name;
    this.code = code;
  }
}
