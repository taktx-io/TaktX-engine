package io.taktx.dto;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class DefinitionMessageEventTriggerDTO extends MessageEventDTO {

  private VariablesDTO variables;

  public DefinitionMessageEventTriggerDTO(String messageName, VariablesDTO variables) {
    super(messageName);
    this.variables = variables;
  }
}
