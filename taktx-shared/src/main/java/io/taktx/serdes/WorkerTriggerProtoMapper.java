/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.serdes;

import io.taktx.dto.AssignmentDefinitionDTO;
import io.taktx.dto.ExternalTaskTriggerDTO;
import io.taktx.dto.PriorityDefinitionDTO;
import io.taktx.dto.ProcessDefinitionKey;
import io.taktx.dto.TaskScheduleDTO;
import io.taktx.dto.UserTaskTriggerDTO;
import io.taktx.dto.VariablesDTO;
import io.taktx.proto.AssignmentDefinitionMessage;
import io.taktx.proto.ExternalTaskTriggerMessage;
import io.taktx.proto.PriorityDefinitionMessage;
import io.taktx.proto.ProcessDefinitionKeyMessage;
import io.taktx.proto.TaskScheduleMessage;
import io.taktx.proto.UserTaskTriggerMessage;
import io.taktx.proto.Uuid;
import io.taktx.proto.VarMap;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Shared DTO ↔ protobuf mapper for worker-facing trigger topics. */
public final class WorkerTriggerProtoMapper {

  private WorkerTriggerProtoMapper() {}

  public static ExternalTaskTriggerMessage toProto(ExternalTaskTriggerDTO dto) {
    ExternalTaskTriggerMessage.Builder builder = ExternalTaskTriggerMessage.newBuilder();
    if (dto == null) {
      return builder.build();
    }
    if (dto.getProcessInstanceId() != null) {
      builder.setProcessInstanceId(toProto(dto.getProcessInstanceId()));
    }
    if (dto.getProcessDefinitionKey() != null) {
      builder.setProcessDefinitionKey(toProto(dto.getProcessDefinitionKey()));
    }
    if (dto.getExternalTaskId() != null) {
      builder.setExternalTaskId(dto.getExternalTaskId());
    }
    if (dto.getElementId() != null) {
      builder.setElementId(dto.getElementId());
    }
    if (dto.getElementInstanceIdPath() != null) {
      builder.addAllElementInstanceIdPath(dto.getElementInstanceIdPath());
    }
    builder.setVariables(toProto(dto.getVariables()));
    if (dto.getHeaders() != null) {
      builder.putAllHeaders(dto.getHeaders());
    }
    return builder.build();
  }

  public static ExternalTaskTriggerDTO toDto(ExternalTaskTriggerMessage message) {
    return new ExternalTaskTriggerDTO(
        message.hasProcessInstanceId() ? toDto(message.getProcessInstanceId()) : null,
        message.hasProcessDefinitionKey() ? toDto(message.getProcessDefinitionKey()) : null,
        emptyToNull(message.getExternalTaskId()),
        emptyToNull(message.getElementId()),
        new ArrayList<>(message.getElementInstanceIdPathList()),
        toVariablesDto(message.getVariables()),
        new HashMap<>(message.getHeadersMap()));
  }

  public static UserTaskTriggerMessage toProto(UserTaskTriggerDTO dto) {
    UserTaskTriggerMessage.Builder builder = UserTaskTriggerMessage.newBuilder();
    if (dto == null) {
      return builder.build();
    }
    if (dto.getProcessInstanceId() != null) {
      builder.setProcessInstanceId(toProto(dto.getProcessInstanceId()));
    }
    if (dto.getProcessDefinitionKey() != null) {
      builder.setProcessDefinitionKey(toProto(dto.getProcessDefinitionKey()));
    }
    if (dto.getUserTaskId() != null) {
      builder.setUserTaskId(dto.getUserTaskId());
    }
    if (dto.getElementInstanceIdPath() != null) {
      builder.addAllElementInstanceIdPath(dto.getElementInstanceIdPath());
    }
    if (dto.getAssignmentDefinition() != null) {
      builder.setAssignmentDefinition(toProto(dto.getAssignmentDefinition()));
    }
    if (dto.getTaskSchedule() != null) {
      builder.setTaskSchedule(toProto(dto.getTaskSchedule()));
    }
    if (dto.getPriorityDefinition() != null) {
      builder.setPriorityDefinition(toProto(dto.getPriorityDefinition()));
    }
    builder.setVariables(toProto(dto.getVariables()));
    return builder.build();
  }

  public static UserTaskTriggerDTO toDto(UserTaskTriggerMessage message) {
    return new UserTaskTriggerDTO(
        message.hasProcessInstanceId() ? toDto(message.getProcessInstanceId()) : null,
        message.hasProcessDefinitionKey() ? toDto(message.getProcessDefinitionKey()) : null,
        emptyToNull(message.getUserTaskId()),
        new ArrayList<>(message.getElementInstanceIdPathList()),
        message.hasAssignmentDefinition() ? toDto(message.getAssignmentDefinition()) : null,
        message.hasTaskSchedule() ? toDto(message.getTaskSchedule()) : null,
        message.hasPriorityDefinition() ? toDto(message.getPriorityDefinition()) : null,
        toVariablesDto(message.getVariables()));
  }

  private static Uuid toProto(UUID uuid) {
    return Uuid.newBuilder()
        .setHigh(uuid.getMostSignificantBits())
        .setLow(uuid.getLeastSignificantBits())
        .build();
  }

  private static UUID toDto(Uuid uuid) {
    return new UUID(uuid.getHigh(), uuid.getLow());
  }

  private static ProcessDefinitionKeyMessage toProto(ProcessDefinitionKey key) {
    return ProcessDefinitionKeyMessage.newBuilder()
        .setProcessDefinitionId(key.getProcessDefinitionId())
        .setVersion(key.getVersion())
        .build();
  }

  private static ProcessDefinitionKey toDto(ProcessDefinitionKeyMessage key) {
    return new ProcessDefinitionKey(emptyToNull(key.getProcessDefinitionId()), key.getVersion());
  }

  private static AssignmentDefinitionMessage toProto(AssignmentDefinitionDTO dto) {
    AssignmentDefinitionMessage.Builder builder = AssignmentDefinitionMessage.newBuilder();
    if (dto.getAssignee() != null) {
      builder.setAssignee(dto.getAssignee());
    }
    if (dto.getCandidateGroups() != null) {
      builder.setCandidateGroups(dto.getCandidateGroups());
    }
    if (dto.getCandidateUsers() != null) {
      builder.setCandidateUsers(dto.getCandidateUsers());
    }
    return builder.build();
  }

  private static AssignmentDefinitionDTO toDto(AssignmentDefinitionMessage message) {
    return new AssignmentDefinitionDTO(
        emptyToNull(message.getAssignee()),
        emptyToNull(message.getCandidateGroups()),
        emptyToNull(message.getCandidateUsers()));
  }

  private static TaskScheduleMessage toProto(TaskScheduleDTO dto) {
    TaskScheduleMessage.Builder builder = TaskScheduleMessage.newBuilder();
    if (dto.getDueDate() != null) {
      builder.setDueDate(dto.getDueDate());
    }
    if (dto.getFollowUpDate() != null) {
      builder.setFollowUpDate(dto.getFollowUpDate());
    }
    return builder.build();
  }

  private static TaskScheduleDTO toDto(TaskScheduleMessage message) {
    return new TaskScheduleDTO(
        emptyToNull(message.getDueDate()), emptyToNull(message.getFollowUpDate()));
  }

  private static PriorityDefinitionMessage toProto(PriorityDefinitionDTO dto) {
    PriorityDefinitionMessage.Builder builder = PriorityDefinitionMessage.newBuilder();
    if (dto.getPriority() != null) {
      builder.setPriority(dto.getPriority());
    }
    return builder.build();
  }

  private static PriorityDefinitionDTO toDto(PriorityDefinitionMessage message) {
    return new PriorityDefinitionDTO(emptyToNull(message.getPriority()));
  }

  private static VarMap toProto(VariablesDTO variables) {
    return VarMap.newBuilder()
        .putAllEntries(variables == null ? Map.of() : variables.getVariables())
        .build();
  }

  private static VariablesDTO toVariablesDto(VarMap variables) {
    Map<String, io.taktx.proto.VariableValue> entries =
        variables == null ? Map.of() : variables.getEntriesMap();
    return VariablesDTO.ofVariableMap(entries);
  }

  private static String emptyToNull(String value) {
    return value == null || value.isEmpty() ? null : value;
  }
}
