package io.taktx.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@EqualsAndHashCode
@NoArgsConstructor
public class IoVariableMappingDTO {

  @JsonProperty("s")
  private String source;

  @JsonProperty("t")
  private String target;

  public IoVariableMappingDTO(String source, String target) {
    this.source = source;
    this.target = target;
  }
}
