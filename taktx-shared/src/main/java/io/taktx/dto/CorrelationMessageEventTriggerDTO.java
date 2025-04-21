package io.taktx.dto;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class CorrelationMessageEventTriggerDTO extends MessageEventDTO {

  private String correlationKey;

  private VariablesDTO variables;

  public CorrelationMessageEventTriggerDTO(
      String messageName, String correlationKey, VariablesDTO variables) {
    super(messageName);
    this.correlationKey = correlationKey;
    this.variables = variables;
  }
}
