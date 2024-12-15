package com.flomaestro.takt.dto.v_1_0_0;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.UUID;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Getter
@ToString
@NoArgsConstructor
@EqualsAndHashCode
public class ExternalTaskTriggerDTO implements SchedulableMessageDTO<UUID> {

  @JsonProperty("pik")
  private UUID processInstanceKey;

  @JsonProperty("pdk")
  private ProcessDefinitionKey processDefinitionKey;

  @JsonProperty("etid")
  private String externalTaskId;

  @JsonProperty("eiid")
  private List<UUID> elementInstanceIdPath;

  @JsonProperty("vrs")
  private VariablesDTO variables;

  public ExternalTaskTriggerDTO(
      UUID processInstanceKey,
      ProcessDefinitionKey processDefinitionKey,
      String externalTaskId,
      List<UUID> elementInstanceIdPath,
      VariablesDTO variables) {
    this.processInstanceKey = processInstanceKey;
    this.processDefinitionKey = processDefinitionKey;
    this.externalTaskId = externalTaskId;
    this.elementInstanceIdPath = elementInstanceIdPath;
    this.variables = variables;
  }

  @JsonIgnore
  @Override
  public UUID getRecordKey(UUID processInstanceKey) {
    return this.processInstanceKey;
  }
}
