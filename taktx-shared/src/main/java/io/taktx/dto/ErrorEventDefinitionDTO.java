package io.taktx.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class ErrorEventDefinitionDTO extends EventDefinitionDTO {

  @JsonProperty("er")
  private String errorRef;

  public ErrorEventDefinitionDTO(String id, String errorRef) {
    super(id, null);
    this.errorRef = errorRef;
  }
}
