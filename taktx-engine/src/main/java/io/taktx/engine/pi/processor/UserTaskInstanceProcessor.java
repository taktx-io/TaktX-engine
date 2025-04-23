/*
 *
 *  * TaktX - A high-performance BPMN engine
 *  * Copyright (c) 2025 TaktX B.V. All rights reserved.
 *  * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 *  * Free use is permitted with up to 3 Kafka partitions. See LICENSE file for details.
 *  * For commercial use or more partitions and features, contact [info@taktx.io] or [https://www.taktx.io/contact].
 *
 */

package io.taktx.engine.pi.processor;

import com.fasterxml.jackson.databind.JsonNode;
import io.taktx.bpmn.AssignmentDefinition;
import io.taktx.bpmn.PriorityDefinition;
import io.taktx.bpmn.TaskSchedule;
import io.taktx.dto.ActtivityStateEnum;
import io.taktx.dto.ExternalTaskResponseTriggerDTO;
import io.taktx.engine.feel.FeelExpressionHandler;
import io.taktx.engine.pd.model.UserTask;
import io.taktx.engine.pi.DirectInstanceResult;
import io.taktx.engine.pi.FlowNodeInstanceProcessingContext;
import io.taktx.engine.pi.ProcessInstanceMapper;
import io.taktx.engine.pi.ProcessInstanceProcessingContext;
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
    extends ActivityInstanceProcessor<UserTask, UserTaskInstance, ExternalTaskResponseTriggerDTO> {
  @Inject
  public UserTaskInstanceProcessor(
      FeelExpressionHandler feelExpressionHandler,
      Clock clock,
      IoMappingProcessor ioMappingProcessor,
      ProcessInstanceMapper processInstanceMapper) {
    super(feelExpressionHandler, ioMappingProcessor, processInstanceMapper, clock);
  }

  @Override
  protected void processStartSpecificActivityInstance(ProcessInstanceProcessingContext processInstanceProcessingContext,
      FlowNodeInstanceProcessingContext flowNodeInstanceProcessingContext,
      UserTaskInstance userTaskInstance,
      String inputFlowId,
      VariableScope flowNodeInstanceVariables) {
    userTaskInstance.setState(ActtivityStateEnum.WAITING);
  }

  @Override
  protected void processContinueSpecificActivityInstance(
      ProcessInstanceProcessingContext processInstanceProcessingContext,
      FlowNodeInstanceProcessingContext flowNodeInstanceProcessingContext,
      int subProcessLevel,
      UserTaskInstance userTaskInstance,
      ExternalTaskResponseTriggerDTO trigger,
      VariableScope variableScope) {

    userTaskInstance.setState(ActtivityStateEnum.FINISHED);
    AssignmentDefinition assignmentDefinition = userTaskInstance.getFlowNode().getAssignmentDefinition();
    if (assignmentDefinition != null) {
      String assignee = feelExpressionHandler.processFeelExpression(assignmentDefinition.getAssignee(),
          variableScope).asText();
      String candidateUsers = feelExpressionHandler.processFeelExpression(assignmentDefinition.getCandidateUsers(),
          variableScope).asText();
      String candidateGroups = feelExpressionHandler.processFeelExpression(assignmentDefinition.getCandidateGroups(),
          variableScope).asText();
    }

    TaskSchedule taskSchedule = userTaskInstance.getFlowNode().getTaskSchedule();
    if (taskSchedule != null) {
      String dueDate = feelExpressionHandler.processFeelExpression(taskSchedule.getDueDate(),
          variableScope).asText();
      String followUpDate = feelExpressionHandler.processFeelExpression(taskSchedule.getFollowUpDate(),
          variableScope).asText();
    }

    PriorityDefinition priorityDefinition = userTaskInstance.getFlowNode().getPriorityDefinition();
    if (priorityDefinition != null) {
      String priority = feelExpressionHandler.processFeelExpression(priorityDefinition.getPriority(),
          variableScope).asText();
    }
  }

  @Override
  protected void processTerminateSpecificActivityInstance(
      ProcessInstanceProcessingContext processInstanceProcessingContext,
      DirectInstanceResult directInstanceResult,
      UserTaskInstance instance,
      VariableScope variables) {

  }
}
