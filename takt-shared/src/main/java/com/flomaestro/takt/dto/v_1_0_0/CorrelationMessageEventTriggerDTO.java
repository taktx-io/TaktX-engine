package com.flomaestro.takt.dto.v_1_0_0;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class CorrelationMessageEventTriggerDTO extends MessageEventDTO {

  @JsonProperty("ck")
  private String correlationKey;

  @JsonProperty("vrs")
  private VariablesDTO variables;

  public CorrelationMessageEventTriggerDTO(
      String messageName, String correlationKey, VariablesDTO variables) {
    super(messageName);
    this.correlationKey = correlationKey;
    this.variables = variables;
  }
}
