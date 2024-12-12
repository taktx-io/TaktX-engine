package com.flomaestro.takt.dto.v_1_0_0;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class EscalationEventDefinitionDTO extends EventDefinitionDTO {

  @JsonProperty("esc")
  private String escalationRef;

  public EscalationEventDefinitionDTO(String id, String escalationRef) {
    super(id, Constants.NONE);
    this.escalationRef = escalationRef;
  }
}
