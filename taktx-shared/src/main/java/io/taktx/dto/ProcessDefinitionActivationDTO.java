package io.taktx.dto;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class ProcessDefinitionActivationDTO extends DefinitionsTriggerDTO {
  private ProcessDefinitionKey processDefinitionKey;

  private ProcessDefinitionStateEnum state;

  public ProcessDefinitionActivationDTO(
      ProcessDefinitionKey processDefinitionKey, ProcessDefinitionStateEnum state) {
    this.state = state;
    this.processDefinitionKey = processDefinitionKey;
  }
}
