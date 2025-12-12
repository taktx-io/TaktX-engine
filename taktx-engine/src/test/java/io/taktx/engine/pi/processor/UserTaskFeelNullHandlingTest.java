/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks All rights reserved.
 * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 * Free use is permitted with up to 3 Kafka partitions per topic. See LICENSE file for details.
 * For commercial use or more partitions and features, contact [https://www.taktx.io/contact].
 */

package io.taktx.engine.pi.processor;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import io.taktx.bpmn.AssignmentDefinition;
import io.taktx.bpmn.PriorityDefinition;
import io.taktx.bpmn.TaskSchedule;
import io.taktx.dto.ExecutionState;
import io.taktx.engine.feel.FeelExpressionHandler;
import io.taktx.engine.pd.model.UserTask;
import io.taktx.engine.pi.InstanceResult;
import io.taktx.engine.pi.ProcessInstanceProcessingContext;
import io.taktx.engine.pi.model.Scope;
import io.taktx.engine.pi.model.UserTaskInstance;
import io.taktx.engine.pi.model.VariableScope;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * TDD tests for FEEL expression null handling in UserTaskInstanceProcessor. These tests document
 * the bug and should fail initially, then pass after fix.
 */
@ExtendWith(MockitoExtension.class)
class UserTaskFeelNullHandlingTest {

  @Mock private FeelExpressionHandler feelExpressionHandler;
  @Mock private IoMappingProcessor ioMappingProcessor;
  @Mock private io.taktx.engine.pi.ProcessInstanceMapper processInstanceMapper;
  @Mock private ProcessInstanceProcessingContext processingContext;
  @Mock private Scope scope;
  @Mock private UserTaskInstance userTaskInstance;
  @Mock private UserTask userTask;
  @Mock private InstanceResult instanceResult;
  @Mock private VariableScope variableScope;

  @Mock private AssignmentDefinition assignmentDef;
  @Mock private TaskSchedule taskSchedule;
  @Mock private PriorityDefinition priorityDef;

  private java.time.Clock clock;
  private UserTaskInstanceProcessor processor;

  @BeforeEach
  void setUp() {
    clock =
        java.time.Clock.fixed(
            java.time.Instant.parse("2025-12-11T10:00:00Z"), java.time.ZoneId.systemDefault());
    processor =
        new UserTaskInstanceProcessor(
            feelExpressionHandler, clock, ioMappingProcessor, processInstanceMapper);
  }

  @Test
  void processStartSpecificActivityInstance_shouldHandleNullAssigneeExpression() {
    // Bug: If FEEL expression returns null, calling .asText() throws NPE
    // Expected: Handle null gracefully, create user task with null assignee
    // Actual (before fix): NullPointerException

    when(assignmentDef.getAssignee()).thenReturn("missingVariable");

    when(userTaskInstance.getFlowNode()).thenReturn(userTask);
    when(userTask.getAssignmentDefinition()).thenReturn(assignmentDef);
    when(userTask.getTaskSchedule()).thenReturn(null);
    when(userTask.getPriorityDefinition()).thenReturn(null);
    when(scope.getVariableScope()).thenReturn(variableScope);
    when(processingContext.getInstanceResult()).thenReturn(instanceResult);

    // Simulate FEEL expression returning null (missing variable)
    when(feelExpressionHandler.processFeelExpression("missingVariable", variableScope))
        .thenReturn(null);

    // Should NOT throw NPE, should handle gracefully
    assertDoesNotThrow(
        () ->
            processor.processStartSpecificActivityInstance(
                processingContext, scope, userTaskInstance, "flow1"));

    verify(userTaskInstance).setState(ExecutionState.ACTIVE);
    verify(instanceResult).addUserTask(any());
  }

  @Test
  void processStartSpecificActivityInstance_shouldHandleNullDueDateExpression() {
    // When due date expression returns null, should handle gracefully

    when(taskSchedule.getDueDate()).thenReturn("invalidExpression");
    when(taskSchedule.getFollowUpDate()).thenReturn("P1D");

    when(userTaskInstance.getFlowNode()).thenReturn(userTask);
    when(userTask.getAssignmentDefinition()).thenReturn(null);
    when(userTask.getTaskSchedule()).thenReturn(taskSchedule);
    when(userTask.getPriorityDefinition()).thenReturn(null);
    when(scope.getVariableScope()).thenReturn(variableScope);
    when(processingContext.getInstanceResult()).thenReturn(instanceResult);

    // Due date returns null, follow-up is valid
    when(feelExpressionHandler.processFeelExpression("invalidExpression", variableScope))
        .thenReturn(null);
    when(feelExpressionHandler.processFeelExpression("P1D", variableScope))
        .thenReturn(com.fasterxml.jackson.databind.node.TextNode.valueOf("P1D"));

    assertDoesNotThrow(
        () ->
            processor.processStartSpecificActivityInstance(
                processingContext, scope, userTaskInstance, "flow1"));

    verify(userTaskInstance).setState(ExecutionState.ACTIVE);
  }

  @Test
  void processStartSpecificActivityInstance_shouldHandleNullPriorityExpression() {
    // When priority expression returns null, should handle gracefully

    when(priorityDef.getPriority()).thenReturn("unknownVariable");

    when(userTaskInstance.getFlowNode()).thenReturn(userTask);
    when(userTask.getAssignmentDefinition()).thenReturn(null);
    when(userTask.getTaskSchedule()).thenReturn(null);
    when(userTask.getPriorityDefinition()).thenReturn(priorityDef);
    when(scope.getVariableScope()).thenReturn(variableScope);
    when(processingContext.getInstanceResult()).thenReturn(instanceResult);

    when(feelExpressionHandler.processFeelExpression("unknownVariable", variableScope))
        .thenReturn(null);

    assertDoesNotThrow(
        () ->
            processor.processStartSpecificActivityInstance(
                processingContext, scope, userTaskInstance, "flow1"));

    verify(userTaskInstance).setState(ExecutionState.ACTIVE);
  }

  @Test
  void processStartSpecificActivityInstance_shouldHandleAllNullExpressions() {
    // When all expressions return null, should create user task with all nulls

    when(assignmentDef.getAssignee()).thenReturn("missingVar1");
    when(assignmentDef.getCandidateGroups()).thenReturn("missingVar2");
    when(assignmentDef.getCandidateUsers()).thenReturn("missingVar3");
    when(taskSchedule.getDueDate()).thenReturn("missingVar4");
    when(taskSchedule.getFollowUpDate()).thenReturn("missingVar5");
    when(priorityDef.getPriority()).thenReturn("missingVar6");

    when(userTaskInstance.getFlowNode()).thenReturn(userTask);
    when(userTask.getAssignmentDefinition()).thenReturn(assignmentDef);
    when(userTask.getTaskSchedule()).thenReturn(taskSchedule);
    when(userTask.getPriorityDefinition()).thenReturn(priorityDef);
    when(scope.getVariableScope()).thenReturn(variableScope);
    when(processingContext.getInstanceResult()).thenReturn(instanceResult);

    // All expressions return null
    when(feelExpressionHandler.processFeelExpression(anyString(), eq(variableScope)))
        .thenReturn(null);

    assertDoesNotThrow(
        () ->
            processor.processStartSpecificActivityInstance(
                processingContext, scope, userTaskInstance, "flow1"));

    verify(userTaskInstance).setState(ExecutionState.ACTIVE);
    verify(instanceResult).addUserTask(any());
  }
}
