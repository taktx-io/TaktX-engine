package io.taktx.dto;

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
public class UserTaskTriggerDTO {

  private UUID processInstanceKey;

  private ProcessDefinitionKey processDefinitionKey;

  private String userTaskId;

  private List<Long> elementInstanceIdPath;

  private AssignmentDefinitionDTO assignmentDefinition;

  private TaskScheduleDTO taskSchedule;

  private PriorityDefinitionDTO priorityDefinition;

  private VariablesDTO variables;

  public UserTaskTriggerDTO(
      UUID processInstanceKey,
      ProcessDefinitionKey processDefinitionKey,
      String userTaskId,
      List<Long> elementInstanceIdPath,
      AssignmentDefinitionDTO assignmentDefinition,
      TaskScheduleDTO taskSchedule,
      PriorityDefinitionDTO priorityDefinition,
      VariablesDTO variables) {
    this.processInstanceKey = processInstanceKey;
    this.processDefinitionKey = processDefinitionKey;
    this.userTaskId = userTaskId;
    this.elementInstanceIdPath = elementInstanceIdPath;
    this.assignmentDefinition = assignmentDefinition;
    this.taskSchedule = taskSchedule;
    this.priorityDefinition = priorityDefinition;
    this.variables = variables;
  }
}
