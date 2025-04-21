package io.taktx.dto;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class EscalationEventDefinitionDTO extends EventDefinitionDTO {

  private String escalationRef;

  public EscalationEventDefinitionDTO(String id, String escalationRef) {
    super(id, null);
    this.escalationRef = escalationRef;
  }
}
