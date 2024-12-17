package com.flomaestro.takt.dto.v_1_0_0;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.UUID;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class StartCommandDTO extends DefinitionsTriggerDTO implements SchedulableMessageDTO {

  @JsonProperty("pi")
  private UUID processInstanceKey;

  @JsonProperty("ppi")
  private UUID parentProcessInstanceKey;

  @JsonProperty("ei")
  private String elementId;

  @JsonProperty("pei")
  private List<String> parentElementIdPath;

  @JsonProperty("peip")
  private List<UUID> parentElementInstancePath;

  @JsonProperty("pd")
  private String processDefinitionId;

  @JsonProperty("vrs")
  private VariablesDTO variables;

  public StartCommandDTO(
      UUID processInstanceKey,
      UUID parentProcessInstanceKey,
      String elementId,
      List<String> parentElementIdPath,
      List<UUID> parentElementInstancePath,
      String processDefinitionId,
      VariablesDTO variables) {
    this.processInstanceKey = processInstanceKey;
    this.parentProcessInstanceKey = parentProcessInstanceKey;
    this.elementId = elementId;
    this.parentElementIdPath = parentElementIdPath;
    this.parentElementInstancePath = parentElementInstancePath;
    this.processDefinitionId = processDefinitionId;
    this.variables = variables;
  }
}
