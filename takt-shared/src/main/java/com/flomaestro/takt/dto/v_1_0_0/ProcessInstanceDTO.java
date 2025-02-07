package com.flomaestro.takt.dto.v_1_0_0;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonFormat.Shape;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

@Getter
@ToString
@EqualsAndHashCode
@SuperBuilder(toBuilder = true)
@NoArgsConstructor
@JsonFormat(shape = Shape.ARRAY)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ProcessInstanceDTO {
  @JsonProperty("pi")
  private UUID processInstanceKey;

  @JsonProperty("ppi")
  private UUID parentProcessInstanceKey;

  @JsonProperty("fni")
  private FlowNodeInstancesDTO flowNodeInstances;

  @JsonProperty("peip")
  private List<String> parentElementIdPath;

  @JsonProperty("peiip")
  private List<Long> parentElementInstancePath;

  @JsonProperty("pdk")
  private ProcessDefinitionKey processDefinitionKey;

  @JsonProperty("pp")
  private boolean propagateAllToParent;

  @JsonProperty("om")
  private Set<IoVariableMappingDTO> outputMappings;

  public ProcessInstanceDTO(
      UUID processInstanceKey,
      UUID parentProcessInstanceKey,
      FlowNodeInstancesDTO flowNodeInstances,
      List<String> parentElementIdPath,
      List<Long> parentElementInstancePath,
      ProcessDefinitionKey processDefinitionKey,
      boolean propagateAllToParent,
      Set<IoVariableMappingDTO> outputMappings) {
    this.processInstanceKey = processInstanceKey;
    this.parentProcessInstanceKey = parentProcessInstanceKey;
    this.flowNodeInstances = flowNodeInstances;
    this.parentElementIdPath = parentElementIdPath;
    this.parentElementInstancePath = parentElementInstancePath;
    this.processDefinitionKey = processDefinitionKey;
    this.propagateAllToParent = propagateAllToParent;
    this.outputMappings = outputMappings;
  }
}
