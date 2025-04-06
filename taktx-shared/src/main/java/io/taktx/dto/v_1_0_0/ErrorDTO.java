package io.taktx.dto.v_1_0_0;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@EqualsAndHashCode
@NoArgsConstructor
public class ErrorDTO {
  @JsonProperty("i")
  private String id;

  @JsonProperty("n")
  private String name;

  @JsonProperty("c")
  private String code;

  public ErrorDTO(String id, String name, String code) {
    this.id = id;
    this.name = name;
    this.code = code;
  }
}
