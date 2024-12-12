package com.flomaestro.takt.dto.v_1_0_0;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.UUID;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class StartNewProcessInstanceTriggerDTO extends StartFlowElementTriggerDTO {

  @JsonProperty("ppik")
  private UUID parentProcessInstanceKey;

  @JsonProperty("peidp")
  private List<String> parentElementIdPath;

  @JsonProperty("peip")
  private List<UUID> parentElementInstancePath;

  @JsonProperty("pd")
  private ProcessDefinitionDTO processDefinition;

  public StartNewProcessInstanceTriggerDTO(
      UUID processInstanceKey,
      UUID parentProcessInstanceKey,
      List<String> parentElementIdPath,
      List<UUID> parentElementInstancePath,
      ProcessDefinitionDTO processDefinition,
      List<String> elementIdPath,
      VariablesDTO variables) {
    super(processInstanceKey, elementIdPath, Constants.NONE, variables);
    this.parentProcessInstanceKey = parentProcessInstanceKey;
    this.parentElementIdPath = parentElementIdPath;
    this.parentElementInstancePath = parentElementInstancePath;
    this.processDefinition = processDefinition;
  }

  @JsonIgnore
  public String getElementId() {
    return getElementIdPath().getFirst();
  }
}
