package io.taktx.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.UUID;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Getter
@ToString
@NoArgsConstructor
@EqualsAndHashCode
@Builder
public class ExternalTaskTriggerDTO implements SchedulableMessageDTO {

  @JsonProperty("pik")
  private UUID processInstanceKey;

  @JsonProperty("pdk")
  private ProcessDefinitionKey processDefinitionKey;

  @JsonProperty("etid")
  private String externalTaskId;

  @JsonProperty("eiid")
  private List<Long> elementInstanceIdPath;

  @JsonProperty("vrs")
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
