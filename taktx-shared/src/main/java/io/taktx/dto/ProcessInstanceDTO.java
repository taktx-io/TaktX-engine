package io.taktx.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonFormat.Shape;
import com.fasterxml.jackson.annotation.JsonInclude;
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
  private UUID processInstanceKey;

  private UUID parentProcessInstanceKey;

  private FlowNodeInstancesDTO flowNodeInstances;

  private List<Long> parentElementInstancePath;

  private ProcessDefinitionKey processDefinitionKey;

  private boolean propagateAllToParent;

  private Set<IoVariableMappingDTO> outputMappings;

  public ProcessInstanceDTO(
      UUID processInstanceKey,
      UUID parentProcessInstanceKey,
      FlowNodeInstancesDTO flowNodeInstances,
      List<Long> parentElementInstancePath,
      ProcessDefinitionKey processDefinitionKey,
      boolean propagateAllToParent,
      Set<IoVariableMappingDTO> outputMappings) {
    this.processInstanceKey = processInstanceKey;
    this.parentProcessInstanceKey = parentProcessInstanceKey;
    this.flowNodeInstances = flowNodeInstances;
    this.parentElementInstancePath = parentElementInstancePath;
    this.processDefinitionKey = processDefinitionKey;
    this.propagateAllToParent = propagateAllToParent;
    this.outputMappings = outputMappings;
  }
}
