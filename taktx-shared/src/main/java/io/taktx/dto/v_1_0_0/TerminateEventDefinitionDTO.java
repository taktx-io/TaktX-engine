package io.taktx.dto.v_1_0_0;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class TerminateEventDefinitionDTO extends EventDefinitionDTO {

  public TerminateEventDefinitionDTO(String id) {
    super(id, null);
  }
}
