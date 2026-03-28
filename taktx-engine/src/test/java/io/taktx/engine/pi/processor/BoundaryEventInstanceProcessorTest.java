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

import io.taktx.dto.ExecutionState;
import io.taktx.engine.feel.FeelExpressionHandler;
import io.taktx.engine.pd.model.BoundaryEvent;
import io.taktx.engine.pi.DirectInstanceResult;
import io.taktx.engine.pi.ProcessInstanceMapper;
import io.taktx.engine.pi.ProcessInstanceProcessingContext;
import io.taktx.engine.pi.model.BoundaryEventInstance;
import io.taktx.engine.pi.model.FlowNodeInstance;
import io.taktx.engine.pi.model.FlowNodeInstances;
import io.taktx.engine.pi.model.Scope;
import io.taktx.engine.pi.model.VariableScope;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BoundaryEventInstanceProcessorTest {

  @Mock private IoMappingProcessor ioMappingProcessor;
  @Mock private FeelExpressionHandler feelExpressionHandler;
  @Mock private ProcessInstanceMapper processInstanceMapper;
  @Mock private ProcessInstanceProcessingContext processingContext;
  @Mock private Scope scope;
  @Mock private VariableScope variableScope;
  @Mock private BoundaryEventInstance boundaryEventInstance;
  @Mock private BoundaryEvent boundaryEvent;
  @Mock private FlowNodeInstances flowNodeInstances;
  @Mock private FlowNodeInstance<?> attachedInstance;
  @Mock private DirectInstanceResult directInstanceResult;

  private Clock clock;
  private BoundaryEventInstanceProcessor processor;

  @BeforeEach
  void setUp() {
    clock = Clock.fixed(Instant.parse("2025-01-01T10:00:00Z"), ZoneId.systemDefault());
    processor =
        new BoundaryEventInstanceProcessor(
            ioMappingProcessor, feelExpressionHandler, processInstanceMapper, clock);
  }

  @Test
  void processStart_shouldAbortAttachedInstance_whenCancelActivityIsTrue() {
    Long attachedInstanceId = 42L;

    when(boundaryEventInstance.getFlowNode()).thenReturn(boundaryEvent);
    when(boundaryEvent.isCancelActivity()).thenReturn(true);
    when(boundaryEventInstance.getAttachedInstanceId()).thenReturn(attachedInstanceId);
    when(scope.getFlowNodeInstances()).thenReturn(flowNodeInstances);
    doReturn(attachedInstance)
        .when(flowNodeInstances)
        .getInstanceWithInstanceId(attachedInstanceId);
    when(scope.getDirectInstanceResult()).thenReturn(directInstanceResult);

    processor.processStartSpecificCatchEventInstance(
        processingContext, scope, variableScope, boundaryEventInstance);

    verify(directInstanceResult).addAbortInstance(attachedInstance);
    verify(boundaryEventInstance).setState(ExecutionState.COMPLETED);
  }

  @Test
  void processStart_shouldNotAbortAttachedInstance_whenCancelActivityIsFalse() {
    when(boundaryEventInstance.getFlowNode()).thenReturn(boundaryEvent);
    when(boundaryEvent.isCancelActivity()).thenReturn(false);

    processor.processStartSpecificCatchEventInstance(
        processingContext, scope, variableScope, boundaryEventInstance);

    verify(scope, never()).getFlowNodeInstances();
    verify(boundaryEventInstance).setState(ExecutionState.COMPLETED);
  }

  @Test
  void processStart_shouldAlwaysSetStateToCompleted() {
    when(boundaryEventInstance.getFlowNode()).thenReturn(boundaryEvent);
    when(boundaryEvent.isCancelActivity()).thenReturn(false);

    processor.processStartSpecificCatchEventInstance(
        processingContext, scope, variableScope, boundaryEventInstance);

    verify(boundaryEventInstance).setState(ExecutionState.COMPLETED);
  }

  @Test
  void processContinue_shouldThrowIllegalStateException() {
    assertThrows(
        IllegalStateException.class,
        () ->
            processor.processContinueSpecificCatchEventInstance(
                processingContext, scope, boundaryEventInstance));
  }
}
