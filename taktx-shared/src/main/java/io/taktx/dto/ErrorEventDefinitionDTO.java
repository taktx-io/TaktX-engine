package io.taktx.dto;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class ErrorEventDefinitionDTO extends EventDefinitionDTO {

  private String errorRef;

  public ErrorEventDefinitionDTO(String id, String errorRef) {
    super(id, null);
    this.errorRef = errorRef;
  }
}
