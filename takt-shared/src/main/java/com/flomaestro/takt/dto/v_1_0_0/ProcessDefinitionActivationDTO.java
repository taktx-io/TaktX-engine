package com.flomaestro.takt.dto.v_1_0_0;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class ProcessDefinitionActivationDTO extends DefinitionsTriggerDTO {
  @JsonProperty("pd")
  private ProcessDefinitionKey processDefinitionKey;

  @JsonProperty("st")
  private ProcessDefinitionStateEnum state;

  public ProcessDefinitionActivationDTO(
      ProcessDefinitionKey processDefinitionKey, ProcessDefinitionStateEnum state) {
    this.state = state;
    this.processDefinitionKey = processDefinitionKey;
  }
}
