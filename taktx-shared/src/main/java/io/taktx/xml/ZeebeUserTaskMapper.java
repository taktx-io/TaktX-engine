package io.taktx.xml;

import io.taktx.bpmn.AssignmentDefinition;
import io.taktx.bpmn.PriorityDefinition;
import io.taktx.bpmn.TUserTask;
import io.taktx.bpmn.TaskHeaders;
import io.taktx.bpmn.TaskSchedule;
import io.taktx.bpmn.UserTask;
import io.taktx.dto.AssignmentDefinitionDTO;
import io.taktx.dto.InputOutputMappingDTO;
import io.taktx.dto.LoopCharacteristicsDTO;
import io.taktx.dto.PriorityDefinitionDTO;
import io.taktx.dto.TaskScheduleDTO;
import io.taktx.dto.UserTaskDTO;
import io.taktx.dto.UserTaskTypeEnum;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class ZeebeUserTaskMapper implements UserTaskMapper {

  @Override
  public UserTaskDTO map(
      TUserTask userTask,
      String parentId,
      LoopCharacteristicsDTO loopCharacteristics,
      InputOutputMappingDTO ioMapping) {
    Optional<UserTask> userTaskElement =
        ExtensionElementHelper.extractExtensionElement(
            userTask.getExtensionElements(), UserTask.class);

    TaskScheduleDTO taskScheduleDTO = null;
    PriorityDefinitionDTO priorityDefinitionDTO = null;

    AssignmentDefinitionDTO assignmentDefinitionDTO = null;
    Optional<AssignmentDefinition> assignmentDefinition = ExtensionElementHelper.extractExtensionElement(
        userTask.getExtensionElements(), AssignmentDefinition.class);
    if (assignmentDefinition.isPresent()) {
      assignmentDefinitionDTO = new AssignmentDefinitionDTO(
          assignmentDefinition.get().getAssignee(),
          assignmentDefinition.get().getCandidateGroups(),
          assignmentDefinition.get().getCandidateUsers());
    }

    Optional<TaskSchedule> taskSchedule = ExtensionElementHelper.extractExtensionElement(
        userTask.getExtensionElements(), TaskSchedule.class);
    if (taskSchedule.isPresent()) {
      taskScheduleDTO = new TaskScheduleDTO(
          taskSchedule.get().getDueDate(),
          taskSchedule.get().getFollowUpDate());
    }

    Optional<PriorityDefinition> priorityDefinition = ExtensionElementHelper.extractExtensionElement(
        userTask.getExtensionElements(), PriorityDefinition.class);
    if (priorityDefinition.isPresent()) {
      priorityDefinitionDTO = new PriorityDefinitionDTO(
          priorityDefinition.get().getPriority());
    }

    UserTaskTypeEnum userTaskTypeEnum;
    if (userTaskElement.isPresent()){
      // Zeebe user task
      userTaskTypeEnum = UserTaskTypeEnum.ZEEBE;
    } else {
      // JobWorker user task
      userTaskTypeEnum = UserTaskTypeEnum.JOBWORKER;
    }

    Map<String, String> headers = new HashMap<>();
    Optional<TaskHeaders> taskDefinitionheaders =
        ExtensionElementHelper.extractExtensionElement(
            userTask.getExtensionElements(), TaskHeaders.class);
    taskDefinitionheaders.ifPresent(
        taskHeaders ->
            taskHeaders
                .getHeader()
                .forEach(header -> headers.put(header.getKey(), header.getValue())));

    return new UserTaskDTO(
        userTask.getId(),
        parentId,
        mapQNameList(userTask.getIncoming()),
        mapQNameList(userTask.getOutgoing()),
        loopCharacteristics,
        ioMapping,
        headers,
        userTaskTypeEnum,
        assignmentDefinitionDTO,
        taskScheduleDTO,
        priorityDefinitionDTO);
  }
}
