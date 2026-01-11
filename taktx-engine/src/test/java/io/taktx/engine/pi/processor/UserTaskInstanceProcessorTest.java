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

import io.taktx.dto.ExecutionState;
import io.taktx.dto.UserTaskResponseResultDTO;
import io.taktx.dto.UserTaskResponseTriggerDTO;
import io.taktx.dto.UserTaskResponseType;
import io.taktx.engine.feel.FeelExpressionHandler;
import io.taktx.engine.pd.model.UserTask;
import io.taktx.engine.pi.DirectInstanceResult;
import io.taktx.engine.pi.InstanceResult;
import io.taktx.engine.pi.ProcessInstanceProcessingContext;
import io.taktx.engine.pi.model.Scope;
import io.taktx.engine.pi.model.UserTaskInfo;
import io.taktx.engine.pi.model.UserTaskInstance;
import io.taktx.engine.pi.model.VariableScope;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UserTaskInstanceProcessorTest {

  @InjectMocks private UserTaskInstanceProcessor processor;

  @Mock private FeelExpressionHandler feelExpressionHandler;
  @Mock private ProcessInstanceProcessingContext processingContext;
  @Mock private Scope scope;
  @Mock private UserTaskInstance userTaskInstance;
  @Mock private UserTask userTask;
  @Mock private InstanceResult instanceResult;
  @Mock private VariableScope variableScope;
  @Mock private DirectInstanceResult directInstanceResult;

  @Test
  void processStartSpecificActivityInstance_shouldCreateUserTaskWithAllProperties() {
    when(userTaskInstance.getFlowNode()).thenReturn(userTask);
    when(userTask.getAssignmentDefinition()).thenReturn(null);
    when(userTask.getTaskSchedule()).thenReturn(null);
    when(userTask.getPriorityDefinition()).thenReturn(null);
    when(processingContext.getInstanceResult()).thenReturn(instanceResult);

    processor.processStartSpecificActivityInstance(
        processingContext, scope, variableScope, userTaskInstance, "flow1");

    verify(userTaskInstance).setState(ExecutionState.ACTIVE);
    verify(instanceResult).addUserTask(any(UserTaskInfo.class));
  }

  @Test
  void processContinueSpecificActivityInstance_shouldCompleteOnSuccess() {
    UserTaskResponseTriggerDTO trigger = mock(UserTaskResponseTriggerDTO.class);
    UserTaskResponseResultDTO responseResult = mock(UserTaskResponseResultDTO.class);

    when(trigger.getUserTaskResponseResult()).thenReturn(responseResult);
    when(responseResult.getResponseType()).thenReturn(UserTaskResponseType.COMPLETED);

    processor.processContinueSpecificActivityInstance(
        processingContext, scope, variableScope, userTaskInstance, trigger);

    verify(userTaskInstance).setState(ExecutionState.COMPLETED);
  }

  @Test
  void processContinueSpecificActivityInstance_shouldHandleEscalation() {
    UserTaskResponseTriggerDTO trigger = mock(UserTaskResponseTriggerDTO.class);
    UserTaskResponseResultDTO responseResult = mock(UserTaskResponseResultDTO.class);

    when(trigger.getUserTaskResponseResult()).thenReturn(responseResult);
    when(responseResult.getResponseType()).thenReturn(UserTaskResponseType.ESCALATION);
    when(responseResult.getCode()).thenReturn("ESCALATION_CODE");
    when(scope.getDirectInstanceResult()).thenReturn(directInstanceResult);

    processor.processContinueSpecificActivityInstance(
        processingContext, scope, variableScope, userTaskInstance, trigger);

    verify(directInstanceResult).addEvent(any());
  }

  @Test
  void processContinueSpecificActivityInstance_shouldHandleError() {
    UserTaskResponseTriggerDTO trigger = mock(UserTaskResponseTriggerDTO.class);
    UserTaskResponseResultDTO responseResult = mock(UserTaskResponseResultDTO.class);

    when(trigger.getUserTaskResponseResult()).thenReturn(responseResult);
    when(responseResult.getResponseType()).thenReturn(UserTaskResponseType.ERROR);
    when(responseResult.getCode()).thenReturn("ERROR_CODE");
    when(scope.getDirectInstanceResult()).thenReturn(directInstanceResult);

    processor.processContinueSpecificActivityInstance(
        processingContext, scope, variableScope, userTaskInstance, trigger);

    verify(directInstanceResult).addEvent(any());
  }

  @Test
  void processAbortSpecificActivityInstance_shouldDoNothing() {
    assertDoesNotThrow(
        () ->
            processor.processAbortSpecificActivityInstance(
                processingContext, scope, variableScope, userTaskInstance));
  }
}
