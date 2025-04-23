package io.taktx.dto;

import java.util.Map;
import java.util.Set;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class UserTaskDTO extends TaskDTO {

  private UserTaskTypeEnum userTaskType;
  private AssignmentDefinitionDTO assignmentDefinition;
  private TaskScheduleDTO taskSchedule;
  private PriorityDefinitionDTO priorityDefinition;
  private Map<String, String> headers;

  public UserTaskDTO(
      String id,
      String parentId,
      Set<String> incoming,
      Set<String> outgoing,
      LoopCharacteristicsDTO loopCharacteristics,
      InputOutputMappingDTO ioMapping,
      Map<String, String> headers,
      UserTaskTypeEnum userTaskType,
      AssignmentDefinitionDTO assignmentDefinitionDTO,
      TaskScheduleDTO taskScheduleDTO,
      PriorityDefinitionDTO priorityDefinitionDTO) {
    super(
        id,
        parentId,
        incoming,
        outgoing,
        loopCharacteristics,
        ioMapping);
    this.headers = headers;
    this.userTaskType = userTaskType;
    this.assignmentDefinition = assignmentDefinitionDTO;
    this.taskSchedule = taskScheduleDTO;
    this.priorityDefinition = priorityDefinitionDTO;
  }
}
