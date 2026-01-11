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

import com.fasterxml.jackson.databind.node.TextNode;
import io.taktx.dto.AbortTriggerDTO;
import io.taktx.dto.ExecutionState;
import io.taktx.dto.VariablesDTO;
import io.taktx.engine.feel.FeelExpressionHandler;
import io.taktx.engine.pd.model.CallActivity;
import io.taktx.engine.pd.model.InputOutputMapping;
import io.taktx.engine.pd.model.NewStartCommand;
import io.taktx.engine.pi.InstanceResult;
import io.taktx.engine.pi.ProcessInstanceException;
import io.taktx.engine.pi.ProcessInstanceProcessingContext;
import io.taktx.engine.pi.model.CallActivityInstance;
import io.taktx.engine.pi.model.Scope;
import io.taktx.engine.pi.model.VariableScope;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CallActivityInstanceProcessorTest {

  @InjectMocks private CallActivityInstanceProcessor processor;

  @Mock private FeelExpressionHandler feelExpressionHandler;
  @Mock private ProcessInstanceProcessingContext processingContext;
  @Mock private Scope scope;
  @Mock private CallActivityInstance callActivityInstance;
  @Mock private CallActivity callActivity;
  @Mock private InstanceResult instanceResult;
  @Mock private VariableScope variableScope;
  @Mock private InputOutputMapping ioMapping;

  @Test
  void processStartSpecificActivityInstance_shouldCreateNewStartCommand() {
    when(callActivityInstance.getFlowNode()).thenReturn(callActivity);
    when(callActivity.getCalledElement()).thenReturn("childProcess");
    when(callActivity.isPropagateAllParentVariables()).thenReturn(false);
    when(callActivity.isPropagateAllChildVariables()).thenReturn(true);
    when(callActivity.getIoMapping()).thenReturn(ioMapping);
    when(ioMapping.getOutputMappings()).thenReturn(Set.of());
    when(variableScope.scopeToDTO()).thenReturn(new VariablesDTO());
    when(feelExpressionHandler.processFeelExpression("childProcess", variableScope))
        .thenReturn(new TextNode("childProcessId"));
    when(processingContext.getInstanceResult()).thenReturn(instanceResult);

    processor.processStartSpecificActivityInstance(
        processingContext, scope, variableScope, callActivityInstance, "flow1");

    verify(callActivityInstance).setState(ExecutionState.ACTIVE);
    verify(callActivityInstance).setChildProcessInstanceId(any(UUID.class));

    ArgumentCaptor<NewStartCommand> captor = ArgumentCaptor.forClass(NewStartCommand.class);
    verify(instanceResult).addNewStartCommand(captor.capture());

    NewStartCommand command = captor.getValue();
    assertNotNull(command);
    assertEquals("childProcessId", command.calledElement());
    assertTrue(command.propagateAllToParent());
  }

  @Test
  void processStartSpecificActivityInstance_shouldPropagateAllParentVariables() {
    when(callActivityInstance.getFlowNode()).thenReturn(callActivity);
    when(callActivity.getCalledElement()).thenReturn("childProcess");
    when(callActivity.isPropagateAllParentVariables()).thenReturn(true);
    when(callActivity.isPropagateAllChildVariables()).thenReturn(false);
    when(callActivity.getIoMapping()).thenReturn(ioMapping);
    when(ioMapping.getOutputMappings()).thenReturn(Set.of());
    when(variableScope.scopeAndParentsToDto()).thenReturn(new VariablesDTO());
    when(feelExpressionHandler.processFeelExpression("childProcess", variableScope))
        .thenReturn(new TextNode("childProcessId"));
    when(processingContext.getInstanceResult()).thenReturn(instanceResult);

    processor.processStartSpecificActivityInstance(
        processingContext, scope, variableScope, callActivityInstance, "flow1");

    verify(variableScope).scopeAndParentsToDto();
  }

  @Test
  void processStartSpecificActivityInstance_shouldAbortWhenCalledElementIsNull() {
    when(callActivityInstance.getFlowNode()).thenReturn(callActivity);
    when(callActivity.getCalledElement()).thenReturn("childProcess");
    when(feelExpressionHandler.processFeelExpression("childProcess", variableScope))
        .thenReturn(null);

    assertThrows(
        ProcessInstanceException.class,
        () ->
            processor.processStartSpecificActivityInstance(
                processingContext, scope, variableScope, callActivityInstance, "flow1"));
  }

  @Test
  void processContinueSpecificActivityInstance_shouldCompleteActivity() {
    processor.processContinueSpecificActivityInstance(
        processingContext, scope, variableScope, callActivityInstance, null);

    verify(callActivityInstance).setState(ExecutionState.COMPLETED);
  }

  @Test
  void processAbortSpecificActivityInstance_shouldAddTerminateCommand() {
    UUID childProcessId = UUID.randomUUID();
    when(callActivityInstance.getChildProcessInstanceId()).thenReturn(childProcessId);
    when(processingContext.getInstanceResult()).thenReturn(instanceResult);

    processor.processAbortSpecificActivityInstance(
        processingContext, scope, variableScope, callActivityInstance);

    ArgumentCaptor<AbortTriggerDTO> captor = ArgumentCaptor.forClass(AbortTriggerDTO.class);
    verify(instanceResult).addTerminateCommand(captor.capture());

    AbortTriggerDTO abortTrigger = captor.getValue();
    assertEquals(childProcessId, abortTrigger.getProcessInstanceId());
  }
}
