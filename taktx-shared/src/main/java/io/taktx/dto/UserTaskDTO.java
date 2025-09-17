/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks All rights reserved.
 * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 * Free use is permitted with up to 3 Kafka partitions per topic. See LICENSE file for details.
 * For commercial use or more partitions and features, contact [https://www.taktx.io/contact].
 */

package io.taktx.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;
import java.util.Map;
import java.util.Set;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@RegisterForReflection
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
    super(id, parentId, incoming, outgoing, loopCharacteristics, ioMapping);
    this.headers = headers;
    this.userTaskType = userTaskType;
    this.assignmentDefinition = assignmentDefinitionDTO;
    this.taskSchedule = taskScheduleDTO;
    this.priorityDefinition = priorityDefinitionDTO;
  }
}
