/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks All rights reserved.
 * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 * Free use is permitted with up to 3 Kafka partitions per topic. See LICENSE file for details.
 * For commercial use or more partitions and features, contact [https://www.taktx.io/contact].
 */

package io.taktx.engine.pi.processor;

import io.taktx.bpmn.AssignmentDefinition;
import io.taktx.bpmn.PriorityDefinition;
import io.taktx.bpmn.TaskSchedule;
import io.taktx.dto.AssignmentDefinitionDTO;
import io.taktx.dto.ExecutionState;
import io.taktx.dto.PriorityDefinitionDTO;
import io.taktx.dto.TaskScheduleDTO;
import io.taktx.dto.UserTaskResponseResultDTO;
import io.taktx.dto.UserTaskResponseTriggerDTO;
import io.taktx.dto.UserTaskResponseType;
import io.taktx.engine.feel.FeelExpressionHandler;
import io.taktx.engine.pd.model.UserTask;
import io.taktx.engine.pi.DirectInstanceResult;
import io.taktx.engine.pi.FlowNodeInstanceProcessingContext;
import io.taktx.engine.pi.ProcessInstanceMapper;
import io.taktx.engine.pi.ProcessInstanceProcessingContext;
import io.taktx.engine.pi.model.ErrorEventSignal;
import io.taktx.engine.pi.model.EscalationEventSignal;
import io.taktx.engine.pi.model.UserTaskInfo;
import io.taktx.engine.pi.model.UserTaskInstance;
import io.taktx.engine.pi.model.VariableScope;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.Clock;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@ApplicationScoped
@NoArgsConstructor
@UserTaskProcessor
@Slf4j
public class UserTaskInstanceProcessor
    extends ActivityInstanceProcessor<UserTask, UserTaskInstance, UserTaskResponseTriggerDTO> {
  @Inject
  public UserTaskInstanceProcessor(
      FeelExpressionHandler feelExpressionHandler,
      Clock clock,
      IoMappingProcessor ioMappingProcessor,
      ProcessInstanceMapper processInstanceMapper) {
    super(feelExpressionHandler, ioMappingProcessor, processInstanceMapper, clock);
  }

  @Override
  protected void processStartSpecificActivityInstance(
      ProcessInstanceProcessingContext processInstanceProcessingContext,
      FlowNodeInstanceProcessingContext flowNodeInstanceProcessingContext,
      UserTaskInstance userTaskInstance,
      String inputFlowId,
      VariableScope flowNodeInstanceVariables) {
    userTaskInstance.setState(ExecutionState.ACTIVE);
    UserTask userTaskNode = userTaskInstance.getFlowNode();
    AssignmentDefinitionDTO assignmentDefinition =
        getProcessedAssignmentDefinition(
            flowNodeInstanceVariables, userTaskNode.getAssignmentDefinition());
    TaskScheduleDTO taskSchedule =
        getProcessedTaskSchedule(flowNodeInstanceVariables, userTaskNode.getTaskSchedule());
    PriorityDefinitionDTO priorityDefinition =
        getProcessedPriorityDefinition(
            flowNodeInstanceVariables, userTaskNode.getPriorityDefinition());
    UserTaskInfo userTaskInfo =
        new UserTaskInfo(
            userTaskInstance.getFlowNode(),
            userTaskInstance,
            flowNodeInstanceVariables,
            assignmentDefinition,
            taskSchedule,
            priorityDefinition);
    processInstanceProcessingContext.getInstanceResult().addUserTask(userTaskInfo);
  }

  private PriorityDefinitionDTO getProcessedPriorityDefinition(
      VariableScope flowNodeInstanceVariablesn, PriorityDefinition priorityDefinition) {
    if (priorityDefinition != null) {
      String priority =
          feelExpressionHandler
              .processFeelExpression(priorityDefinition.getPriority(), flowNodeInstanceVariablesn)
              .asText();
      return new PriorityDefinitionDTO(priority);
    } else {
      return null;
    }
  }

  private TaskScheduleDTO getProcessedTaskSchedule(
      VariableScope flowNodeInstanceVariables, TaskSchedule taskSchedule) {
    if (taskSchedule != null) {
      String dueDate =
          feelExpressionHandler
              .processFeelExpression(taskSchedule.getDueDate(), flowNodeInstanceVariables)
              .asText();
      String followupDate =
          feelExpressionHandler
              .processFeelExpression(taskSchedule.getFollowUpDate(), flowNodeInstanceVariables)
              .asText();
      return new TaskScheduleDTO(dueDate, followupDate);
    } else {
      return null;
    }
  }

  private AssignmentDefinitionDTO getProcessedAssignmentDefinition(
      VariableScope flowNodeInstanceVariables, AssignmentDefinition assignmentDefinition) {
    if (assignmentDefinition != null) {
      String assignee =
          feelExpressionHandler
              .processFeelExpression(assignmentDefinition.getAssignee(), flowNodeInstanceVariables)
              .asText();
      String candidateGroups =
          feelExpressionHandler
              .processFeelExpression(
                  assignmentDefinition.getCandidateGroups(), flowNodeInstanceVariables)
              .asText();
      String candidateUsers =
          feelExpressionHandler
              .processFeelExpression(
                  assignmentDefinition.getCandidateUsers(), flowNodeInstanceVariables)
              .asText();
      return new AssignmentDefinitionDTO(assignee, candidateGroups, candidateUsers);
    } else {
      return null;
    }
  }

  @Override
  protected void processContinueSpecificActivityInstance(
      ProcessInstanceProcessingContext processInstanceProcessingContext,
      FlowNodeInstanceProcessingContext flowNodeInstanceProcessingContext,
      UserTaskInstance userTaskInstance,
      UserTaskResponseTriggerDTO trigger,
      VariableScope variableScope) {

    UserTaskResponseResultDTO responseResult = trigger.getUserTaskResponseResult();

    if (UserTaskResponseType.COMPLETED == responseResult.getResponseType()) {
      userTaskInstance.setState(ExecutionState.COMPLETED);
    } else if (UserTaskResponseType.ERROR == responseResult.getResponseType()) {
      handleError(
          flowNodeInstanceProcessingContext.getDirectInstanceResult(),
          userTaskInstance,
          responseResult);
    } else if (UserTaskResponseType.ESCALATION == responseResult.getResponseType()) {
      handleEscalation(
          flowNodeInstanceProcessingContext.getDirectInstanceResult(),
          userTaskInstance,
          responseResult);
    }
  }

  private void handleEscalation(
      DirectInstanceResult directInstanceResult,
      UserTaskInstance userTaskInstance,
      UserTaskResponseResultDTO responseResult) {
    directInstanceResult.addEvent(
        new EscalationEventSignal(
            userTaskInstance, responseResult.getCode(), responseResult.getMessage()));
  }

  private void handleError(
      DirectInstanceResult directInstanceResult,
      UserTaskInstance userTaskInstance,
      UserTaskResponseResultDTO responseResult) {
    directInstanceResult.addEvent(
        new ErrorEventSignal(
            userTaskInstance, responseResult.getCode(), responseResult.getMessage()));
  }

  @Override
  protected void processTerminateSpecificActivityInstance(
      ProcessInstanceProcessingContext processInstanceProcessingContext,
      FlowNodeInstanceProcessingContext flowNodeInstanceProcessingContext,
      UserTaskInstance instance,
      VariableScope variables) {
    // no specific termination logic for user tasks
  }
}
