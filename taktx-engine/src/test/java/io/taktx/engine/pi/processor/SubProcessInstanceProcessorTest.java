/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */

package io.taktx.engine.pi.processor;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import io.taktx.dto.AbortTriggerDTO;
import io.taktx.dto.ContinueFlowElementTriggerDTO;
import io.taktx.dto.ExecutionState;
import io.taktx.dto.VariablesDTO;
import io.taktx.engine.feel.FeelExpressionHandler;
import io.taktx.engine.pd.model.FlowElements;
import io.taktx.engine.pd.model.SubProcess;
import io.taktx.engine.pi.PathExtractor;
import io.taktx.engine.pi.ProcessInstanceMapper;
import io.taktx.engine.pi.ProcessInstanceProcessingContext;
import io.taktx.engine.pi.ScopeProcessor;
import io.taktx.engine.pi.model.ProcessInstance;
import io.taktx.engine.pi.model.Scope;
import io.taktx.engine.pi.model.SubProcessInstance;
import io.taktx.engine.pi.model.VariableScope;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SubProcessInstanceProcessorTest {

  @Mock private FeelExpressionHandler feelExpressionHandler;
  @Mock private IoMappingProcessor ioMappingProcessor;
  @Mock private ScopeProcessor scopeProcessor;
  @Mock private ProcessInstanceMapper processInstanceMapper;
  @Mock private PathExtractor pathExtractor;
  @Mock private ProcessInstanceProcessingContext processingContext;
  @Mock private ProcessInstance processInstance;
  @Mock private Scope scope;
  @Mock private Scope childScope;
  @Mock private VariableScope variableScope;
  @Mock private SubProcessInstance subProcessInstance;
  @Mock private SubProcess subProcess;
  @Mock private FlowElements flowElements;
  @Mock private FlowElements subProcessElements;

  private Clock clock;
  private SubProcessInstanceProcessor processor;

  @BeforeEach
  void setUp() {
    clock = Clock.fixed(Instant.parse("2025-01-01T10:00:00Z"), ZoneId.systemDefault());
    processor =
        new SubProcessInstanceProcessor(
            feelExpressionHandler,
            ioMappingProcessor,
            scopeProcessor,
            processInstanceMapper,
            clock,
            pathExtractor);
  }

  @Test
  void processStart_shouldCreateChildScope() {
    List<Long> instancePath = List.of(1L);

    when(subProcessInstance.getFlowNode()).thenReturn(subProcess);
    when(subProcessInstance.getFlowElements()).thenReturn(subProcessElements);
    when(subProcess.getElements()).thenReturn(subProcessElements);
    when(subProcessElements.getIndex()).thenReturn(new java.util.ArrayList<>());
    when(scope.selectChildScope(subProcessInstance, subProcessElements)).thenReturn(childScope);
    when(scope.getFlowElements()).thenReturn(flowElements);
    when(flowElements.getIndex()).thenReturn(new java.util.ArrayList<>());
    when(pathExtractor.getInstancePath(subProcessInstance)).thenReturn(instancePath);
    when(childScope.getState()).thenReturn(ExecutionState.COMPLETED);

    processor.processStartSpecificActivityInstance(
        processingContext, scope, variableScope, subProcessInstance, "flow1");

    verify(subProcessInstance).setScope(childScope);
  }

  @Test
  void processStart_shouldSetStateToActive() {
    List<Long> instancePath = List.of(1L);

    when(subProcessInstance.getFlowNode()).thenReturn(subProcess);
    when(subProcessInstance.getFlowElements()).thenReturn(subProcessElements);
    when(subProcess.getElements()).thenReturn(subProcessElements);
    when(subProcessElements.getIndex()).thenReturn(new java.util.ArrayList<>());
    when(scope.selectChildScope(subProcessInstance, subProcessElements)).thenReturn(childScope);
    when(scope.getFlowElements()).thenReturn(flowElements);
    when(flowElements.getIndex()).thenReturn(new java.util.ArrayList<>());
    when(pathExtractor.getInstancePath(subProcessInstance)).thenReturn(instancePath);
    when(childScope.getState()).thenReturn(ExecutionState.COMPLETED);

    processor.processStartSpecificActivityInstance(
        processingContext, scope, variableScope, subProcessInstance, "flow1");

    verify(subProcessInstance).setState(ExecutionState.ACTIVE);
  }

  @Test
  void processStart_shouldDelegateToScopeProcessor() {
    List<Long> instancePath = List.of(1L);

    when(subProcessInstance.getFlowNode()).thenReturn(subProcess);
    when(subProcessInstance.getFlowElements()).thenReturn(subProcessElements);
    when(subProcess.getElements()).thenReturn(subProcessElements);
    when(subProcessElements.getIndex()).thenReturn(new java.util.ArrayList<>());
    when(scope.selectChildScope(subProcessInstance, subProcessElements)).thenReturn(childScope);
    when(scope.getFlowElements()).thenReturn(flowElements);
    when(flowElements.getIndex()).thenReturn(new java.util.ArrayList<>());
    when(pathExtractor.getInstancePath(subProcessInstance)).thenReturn(instancePath);
    when(childScope.getState()).thenReturn(ExecutionState.COMPLETED);

    processor.processStartSpecificActivityInstance(
        processingContext, scope, variableScope, subProcessInstance, "flow1");

    verify(scopeProcessor)
        .processStart(
            eq(instancePath),
            isNull(),
            eq(VariablesDTO.empty()),
            eq(processingContext),
            eq(childScope),
            eq(variableScope));
  }

  @Test
  void processStart_shouldBubbleUpEvents() {
    List<Long> instancePath = List.of(1L);

    when(subProcessInstance.getFlowNode()).thenReturn(subProcess);
    when(subProcessInstance.getFlowElements()).thenReturn(subProcessElements);
    when(subProcess.getElements()).thenReturn(subProcessElements);
    when(subProcessElements.getIndex()).thenReturn(new java.util.ArrayList<>());
    when(scope.selectChildScope(subProcessInstance, subProcessElements)).thenReturn(childScope);
    when(scope.getFlowElements()).thenReturn(flowElements);
    when(flowElements.getIndex()).thenReturn(new java.util.ArrayList<>());
    when(pathExtractor.getInstancePath(subProcessInstance)).thenReturn(instancePath);
    when(childScope.getState()).thenReturn(ExecutionState.COMPLETED);

    processor.processStartSpecificActivityInstance(
        processingContext, scope, variableScope, subProcessInstance, "flow1");

    verify(scopeProcessor).bubbleUpEvents(scope, subProcessInstance);
  }

  @Test
  void processStart_shouldSyncStateWithChildScope() {
    List<Long> instancePath = List.of(1L);

    when(subProcessInstance.getFlowNode()).thenReturn(subProcess);
    when(subProcessInstance.getFlowElements()).thenReturn(subProcessElements);
    when(subProcess.getElements()).thenReturn(subProcessElements);
    when(subProcessElements.getIndex()).thenReturn(new java.util.ArrayList<>());
    when(scope.selectChildScope(subProcessInstance, subProcessElements)).thenReturn(childScope);
    when(scope.getFlowElements()).thenReturn(flowElements);
    when(flowElements.getIndex()).thenReturn(new java.util.ArrayList<>());
    when(pathExtractor.getInstancePath(subProcessInstance)).thenReturn(instancePath);
    when(childScope.getState()).thenReturn(ExecutionState.ACTIVE);

    processor.processStartSpecificActivityInstance(
        processingContext, scope, variableScope, subProcessInstance, "flow1");

    // Verify state is synced from child scope
    verify(subProcessInstance, atLeastOnce()).setState(ExecutionState.ACTIVE);
  }

  @Test
  void processContinue_shouldSyncStateWithChildScope() {
    ContinueFlowElementTriggerDTO trigger = mock(ContinueFlowElementTriggerDTO.class);

    when(subProcessInstance.getScope()).thenReturn(childScope);
    when(childScope.getState()).thenReturn(ExecutionState.COMPLETED);

    processor.processContinueSpecificActivityInstance(
        processingContext, scope, variableScope, subProcessInstance, trigger);

    verify(subProcessInstance).setState(ExecutionState.COMPLETED);
  }

  @Test
  void processAbort_shouldDelegateAbortToChildScope() {
    UUID processInstanceId = UUID.randomUUID();

    when(subProcessInstance.getScope()).thenReturn(childScope);
    when(processingContext.getProcessInstance()).thenReturn(processInstance);
    when(processInstance.getProcessInstanceId()).thenReturn(processInstanceId);

    processor.processAbortSpecificActivityInstance(
        processingContext, scope, variableScope, subProcessInstance);

    ArgumentCaptor<AbortTriggerDTO> triggerCaptor = ArgumentCaptor.forClass(AbortTriggerDTO.class);
    verify(scopeProcessor)
        .processAbort(
            eq(processingContext), eq(childScope), eq(variableScope), triggerCaptor.capture());

    AbortTriggerDTO capturedTrigger = triggerCaptor.getValue();
    assertEquals(processInstanceId, capturedTrigger.getProcessInstanceId());
    assertEquals(List.of(), capturedTrigger.getElementInstanceIdPath());
  }
}
