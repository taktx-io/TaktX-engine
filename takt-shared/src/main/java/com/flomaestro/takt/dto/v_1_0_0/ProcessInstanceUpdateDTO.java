package com.flomaestro.takt.dto.v_1_0_0;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.UUID;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Getter
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class ProcessInstanceUpdateDTO extends InstanceUpdateDTO {
  @JsonProperty("pp")
  private UUID parentProcessInstanceKey;

  @JsonProperty("pe")
  private List<String> parentElementIdPath;

  @JsonProperty("pi")
  private List<UUID> parentElementInstancePath;

  @JsonProperty("pd")
  private ProcessDefinitionKey processDefinitionKey;

  @JsonProperty("fi")
  private FlowNodeInstancesDTO flowNodeInstances;

  @JsonProperty("v")
  private VariablesDTO variables;

  @JsonProperty("t")
  private long processTime;

  public ProcessInstanceUpdateDTO(
      UUID processInstanceKey,
      UUID parentProcessInstanceKey,
      List<String> parentElementIdPath,
      List<UUID> parentElementInstancePath,
      ProcessDefinitionKey processDefinitionKey,
      FlowNodeInstancesDTO flowNodeInstances,
      VariablesDTO variables,
      long processTime) {

    super(processInstanceKey);
    this.parentProcessInstanceKey = parentProcessInstanceKey;
    this.parentElementIdPath = parentElementIdPath;
    this.parentElementInstancePath = parentElementInstancePath;
    this.processDefinitionKey = processDefinitionKey;
    this.flowNodeInstances = flowNodeInstances;
    this.variables = variables;
    this.processTime = processTime;
  }

  public ProcessInstanceUpdateDTO(
      ProcessInstanceDTO processInstance, VariablesDTO variables, long processTime) {
    this(
        processInstance.getProcessInstanceKey(),
        processInstance.getParentProcessInstanceKey(),
        processInstance.getParentElementIdPath(),
        processInstance.getParentElementInstancePath(),
        processInstance.getProcessDefinitionKey(),
        processInstance.getFlowNodeInstances(),
        variables,
        processTime);
  }
}
