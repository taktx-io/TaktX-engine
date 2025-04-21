package io.taktx.dto;

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
  private UUID parentProcessInstanceKey;

  private List<Long> parentElementInstancePath;

  private ProcessDefinitionKey processDefinitionKey;

  private FlowNodeInstancesDTO flowNodeInstances;

  private VariablesDTO variables;

  private long processTime;

  public ProcessInstanceUpdateDTO(
      UUID parentProcessInstanceKey,
      List<Long> parentElementInstancePath,
      ProcessDefinitionKey processDefinitionKey,
      FlowNodeInstancesDTO flowNodeInstances,
      VariablesDTO variables,
      long processTime) {

    this.parentProcessInstanceKey = parentProcessInstanceKey;
    this.parentElementInstancePath = parentElementInstancePath;
    this.processDefinitionKey = processDefinitionKey;
    this.flowNodeInstances = flowNodeInstances;
    this.variables = variables;
    this.processTime = processTime;
  }

  public ProcessInstanceUpdateDTO(
      ProcessInstanceDTO processInstance, VariablesDTO variables, long processTime) {
    this(
        processInstance.getParentProcessInstanceKey(),
        processInstance.getParentElementInstancePath(),
        processInstance.getProcessDefinitionKey(),
        processInstance.getFlowNodeInstances(),
        variables,
        processTime);
  }
}
