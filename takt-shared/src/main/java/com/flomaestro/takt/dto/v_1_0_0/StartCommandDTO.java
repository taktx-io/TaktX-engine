package com.flomaestro.takt.dto.v_1_0_0;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class StartCommandDTO extends ProcessInstanceTriggerDTO implements SchedulableMessageDTO {

  @JsonProperty("ppi")
  private UUID parentProcessInstanceKey;

  @JsonProperty("pei")
  private List<String> parentElementIdPath;

  @JsonProperty("peip")
  private List<UUID> parentElementInstancePath;

  @JsonProperty("pd")
  private ProcessDefinitionKey processDefinitionKey;

  @JsonProperty("pp")
  private boolean propagateAllToParent;

  @JsonProperty("om")
  private Set<IoVariableMappingDTO> outputMappings;

  public StartCommandDTO(
      UUID processInstanceKey,
      String elementId,
      List<String> parentElementIdPath,
      List<UUID> parentElementInstancePath,
      ProcessDefinitionKey processDefinitionKey,
      VariablesDTO variables) {
    this(
        processInstanceKey,
        Constants.NONE_UUID,
        elementId,
        parentElementIdPath,
        parentElementInstancePath,
        processDefinitionKey,
        variables,
        false,
        Set.of());
  }

  public StartCommandDTO(
      UUID processInstanceKey,
      UUID parentProcessInstanceKey,
      String elementId,
      List<String> parentElementIdPath,
      List<UUID> parentElementInstancePath,
      ProcessDefinitionKey processDefinitionKey,
      VariablesDTO variables,
      boolean propagateAllToParent,
      Set<IoVariableMappingDTO> outputMappings) {
    super(processInstanceKey, List.of(elementId), variables);
    this.parentProcessInstanceKey = parentProcessInstanceKey;
    this.parentElementIdPath = parentElementIdPath;
    this.parentElementInstancePath = parentElementInstancePath;
    this.processDefinitionKey = processDefinitionKey;
    this.propagateAllToParent = propagateAllToParent;
    this.outputMappings = outputMappings;
  }
}
