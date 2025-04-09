package io.taktx.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class DefinitionMessageEventTriggerDTO extends MessageEventDTO {

  @JsonProperty("v")
  private VariablesDTO variables;

  public DefinitionMessageEventTriggerDTO(String messageName, VariablesDTO variables) {
    super(messageName);
    this.variables = variables;
  }
}
