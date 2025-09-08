/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks All rights reserved.
 * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 * Free use is permitted with up to 3 Kafka partitions per topic. See LICENSE file for details.
 * For commercial use or more partitions and features, contact [https://www.taktx.io/contact].
 */

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

  private UUID processInstanceId;

  private ProcessDefinitionKey processDefinitionKey;

  private String userTaskId;

  private List<Long> elementInstanceIdPath;

  private AssignmentDefinitionDTO assignmentDefinition;

  private TaskScheduleDTO taskSchedule;

  private PriorityDefinitionDTO priorityDefinition;

  private VariablesDTO variables;

  public UserTaskTriggerDTO(
      UUID processInstanceId,
      ProcessDefinitionKey processDefinitionKey,
      String userTaskId,
      List<Long> elementInstanceIdPath,
      AssignmentDefinitionDTO assignmentDefinition,
      TaskScheduleDTO taskSchedule,
      PriorityDefinitionDTO priorityDefinition,
      VariablesDTO variables) {
    this.processInstanceId = processInstanceId;
    this.processDefinitionKey = processDefinitionKey;
    this.userTaskId = userTaskId;
    this.elementInstanceIdPath = elementInstanceIdPath;
    this.assignmentDefinition = assignmentDefinition;
    this.taskSchedule = taskSchedule;
    this.priorityDefinition = priorityDefinition;
    this.variables = variables;
  }
}
