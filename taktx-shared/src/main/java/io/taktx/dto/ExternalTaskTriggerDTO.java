package io.taktx.dto;

import java.util.List;
import java.util.UUID;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Getter
@ToString
@NoArgsConstructor
@EqualsAndHashCode
@Builder
public class ExternalTaskTriggerDTO implements SchedulableMessageDTO {

  @Setter private UUID processInstanceKey;

  private ProcessDefinitionKey processDefinitionKey;

  private String externalTaskId;

  private List<Long> elementInstanceIdPath;

  private VariablesDTO variables;

  public ExternalTaskTriggerDTO(
      UUID processInstanceKey,
      ProcessDefinitionKey processDefinitionKey,
      String externalTaskId,
      List<Long> elementInstanceIdPath,
      VariablesDTO variables) {
    this.processInstanceKey = processInstanceKey;
    this.processDefinitionKey = processDefinitionKey;
    this.externalTaskId = externalTaskId;
    this.elementInstanceIdPath = elementInstanceIdPath;
    this.variables = variables;
  }
}
