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

import com.fasterxml.jackson.databind.node.TextNode;
import io.taktx.engine.feel.FeelExpressionHandler;
import io.taktx.engine.pd.model.*;
import io.taktx.engine.pi.DirectInstanceResult;
import io.taktx.engine.pi.InstanceResult;
import io.taktx.engine.pi.ProcessInstanceMapper;
import io.taktx.engine.pi.ProcessInstanceProcessingContext;
import io.taktx.engine.pi.model.ErrorEventSignal;
import io.taktx.engine.pi.model.EscalationEventSignal;
import io.taktx.engine.pi.model.Scope;
import io.taktx.engine.pi.model.ThrowEventInstance;
import io.taktx.engine.pi.model.VariableScope;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ThrowEventInstanceProcessorTest {

  @Mock private IoMappingProcessor ioMappingProcessor;
  @Mock private ProcessInstanceMapper processInstanceMapper;
  @Mock private FeelExpressionHandler feelExpressionHandler;
  @Mock private ProcessInstanceProcessingContext processingContext;
  @Mock private Scope scope;
  @Mock private ThrowEventInstance<?> throwEventInstance;
  @Mock private ThrowEvent throwEvent;
  @Mock private InstanceResult instanceResult;
  @Mock private DirectInstanceResult directInstanceResult;
  @Mock private VariableScope variableScope;

  private Clock clock;
  private TestThrowEventInstanceProcessor processor;

  @BeforeEach
  void setUp() {
    clock = Clock.fixed(Instant.parse("2025-01-01T10:00:00Z"), ZoneId.systemDefault());
    processor =
        new TestThrowEventInstanceProcessor(
            ioMappingProcessor, processInstanceMapper, feelExpressionHandler, clock);
  }

  @Test
  void processStartSpecificEventInstance_shouldAbortScopeOnTerminateEvent() {
    TerminateEventDefinition terminateDef = mock(TerminateEventDefinition.class);

    when(throwEventInstance.getFlowNode()).thenReturn(throwEvent);
    when(throwEvent.getTerminateEventDefinition()).thenReturn(Optional.of(terminateDef));
    when(throwEvent.getSignalEventDefinition()).thenReturn(Optional.empty());
    when(throwEvent.getErrorEventDefinition()).thenReturn(Optional.empty());
    when(throwEvent.getEscalationEventDefinition()).thenReturn(Optional.empty());
    when(throwEvent.getLinkventDefinition()).thenReturn(Optional.empty());
    when(scope.getDirectInstanceResult()).thenReturn(directInstanceResult);

    processor.processStartSpecificEventInstance(
        processingContext, scope, variableScope, throwEventInstance, "flow1");

    verify(directInstanceResult).setAbortScope();
  }

  @Test
  void processStartSpecificEventInstance_shouldAddSignalToInstanceResult() {
    SignalEventDefinition signalDef = mock(SignalEventDefinition.class);
    SignalEvent signal = mock(SignalEvent.class);

    when(throwEventInstance.getFlowNode()).thenReturn(throwEvent);
    when(throwEvent.getTerminateEventDefinition()).thenReturn(Optional.empty());
    when(throwEvent.getSignalEventDefinition()).thenReturn(Optional.of(signalDef));
    when(signalDef.getReferencedSignal()).thenReturn(signal);
    when(signal.name()).thenReturn("signalName");
    when(throwEvent.getErrorEventDefinition()).thenReturn(Optional.empty());
    when(throwEvent.getEscalationEventDefinition()).thenReturn(Optional.empty());
    when(throwEvent.getLinkventDefinition()).thenReturn(Optional.empty());
    when(feelExpressionHandler.processFeelExpression("signalName", variableScope))
        .thenReturn(new TextNode("resolvedSignalName"));
    when(processingContext.getInstanceResult()).thenReturn(instanceResult);

    processor.processStartSpecificEventInstance(
        processingContext, scope, variableScope, throwEventInstance, "flow1");

    verify(instanceResult).addSignal("resolvedSignalName");
  }

  @Test
  void processStartSpecificEventInstance_shouldAddErrorEventToDirectResult() {
    ErrorEventDefinition errorDef = mock(ErrorEventDefinition.class);
    ErrorEvent error = mock(ErrorEvent.class);

    when(throwEventInstance.getFlowNode()).thenReturn(throwEvent);
    when(throwEvent.getTerminateEventDefinition()).thenReturn(Optional.empty());
    when(throwEvent.getSignalEventDefinition()).thenReturn(Optional.empty());
    when(throwEvent.getErrorEventDefinition()).thenReturn(Optional.of(errorDef));
    when(errorDef.getReferencedError()).thenReturn(error);
    when(error.code()).thenReturn("ERROR_CODE");
    when(throwEvent.getEscalationEventDefinition()).thenReturn(Optional.empty());
    when(throwEvent.getLinkventDefinition()).thenReturn(Optional.empty());
    when(scope.getDirectInstanceResult()).thenReturn(directInstanceResult);

    processor.processStartSpecificEventInstance(
        processingContext, scope, variableScope, throwEventInstance, "flow1");

    ArgumentCaptor<ErrorEventSignal> captor = ArgumentCaptor.forClass(ErrorEventSignal.class);
    verify(directInstanceResult).addEvent(captor.capture());

    ErrorEventSignal signal = captor.getValue();
    assertEquals("ERROR_CODE", signal.getCode());
    assertSame(throwEventInstance, signal.getCurrentInstance());
  }

  @Test
  void processStartSpecificEventInstance_shouldAddEscalationEventToDirectResult() {
    EscalationEventDefinition escalationDef = mock(EscalationEventDefinition.class);
    EscalationEvent escalation = mock(EscalationEvent.class);

    when(throwEventInstance.getFlowNode()).thenReturn(throwEvent);
    when(throwEvent.getTerminateEventDefinition()).thenReturn(Optional.empty());
    when(throwEvent.getSignalEventDefinition()).thenReturn(Optional.empty());
    when(throwEvent.getErrorEventDefinition()).thenReturn(Optional.empty());
    when(throwEvent.getEscalationEventDefinition()).thenReturn(Optional.of(escalationDef));
    when(escalationDef.getReferencedEscalation()).thenReturn(escalation);
    when(escalation.code()).thenReturn("ESCALATION_CODE");
    when(throwEvent.getLinkventDefinition()).thenReturn(Optional.empty());
    when(scope.getDirectInstanceResult()).thenReturn(directInstanceResult);

    processor.processStartSpecificEventInstance(
        processingContext, scope, variableScope, throwEventInstance, "flow1");

    ArgumentCaptor<EscalationEventSignal> captor =
        ArgumentCaptor.forClass(EscalationEventSignal.class);
    verify(directInstanceResult).addEvent(captor.capture());

    EscalationEventSignal signal = captor.getValue();
    assertEquals("ESCALATION_CODE", signal.getCode());
    assertSame(throwEventInstance, signal.getCurrentInstance());
  }

  // Test implementation of abstract class
  private static class TestThrowEventInstanceProcessor
      extends ThrowEventInstanceProcessor<ThrowEvent, ThrowEventInstance<?>> {

    TestThrowEventInstanceProcessor(
        IoMappingProcessor ioMappingProcessor,
        ProcessInstanceMapper processInstanceMapper,
        FeelExpressionHandler feelExpressionHandler,
        Clock clock) {
      super(ioMappingProcessor, processInstanceMapper, feelExpressionHandler, clock);
    }

    @Override
    protected void processStartSpecificThrowEventInstance(
        ProcessInstanceProcessingContext processInstanceProcessingContext,
        Scope scope,
        ThrowEventInstance<?> flowNodeInstance) {
      // Test implementation
    }

    @Override
    protected void processAbortSpecificFlowNodeInstance(
        ProcessInstanceProcessingContext processInstanceProcessingContext,
        Scope scope,
        VariableScope variableScope,
        ThrowEventInstance<?> instance) {
      // Test implementation
    }
  }
}
