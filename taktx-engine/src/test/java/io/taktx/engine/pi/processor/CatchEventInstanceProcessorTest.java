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
import io.taktx.dto.ExecutionState;
import io.taktx.engine.feel.FeelExpressionHandler;
import io.taktx.engine.pd.model.*;
import io.taktx.engine.pi.InstanceResult;
import io.taktx.engine.pi.ProcessInstanceMapper;
import io.taktx.engine.pi.ProcessInstanceProcessingContext;
import io.taktx.engine.pi.model.CatchEventInstance;
import io.taktx.engine.pi.model.NewCorrelationSubscriptionMessageEventInfo;
import io.taktx.engine.pi.model.NewInstanceSignalSubscriptionInfo;
import io.taktx.engine.pi.model.ScheduledContinuationInfo;
import io.taktx.engine.pi.model.Scope;
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
class CatchEventInstanceProcessorTest {

  @Mock private IoMappingProcessor ioMappingProcessor;
  @Mock private ProcessInstanceMapper processInstanceMapper;
  @Mock private FeelExpressionHandler feelExpressionHandler;
  @Mock private ProcessInstanceProcessingContext processingContext;
  @Mock private Scope scope;
  @Mock private CatchEventInstance<?> catchEventInstance;
  @Mock private CatchEvent catchEvent;
  @Mock private InstanceResult instanceResult;
  @Mock private VariableScope variableScope;

  private Clock clock;
  private TestCatchEventInstanceProcessor processor;

  @BeforeEach
  void setUp() {
    clock = Clock.fixed(Instant.parse("2025-01-01T10:00:00Z"), ZoneId.systemDefault());
    processor =
        new TestCatchEventInstanceProcessor(
            ioMappingProcessor, processInstanceMapper, feelExpressionHandler, clock);
  }

  @Test
  void processStartSpecificEventInstance_shouldCompleteWhenNoEventDefinitions() {
    when(catchEventInstance.getFlowNode()).thenReturn(catchEvent);
    when(catchEvent.getSignalEventDefinition()).thenReturn(Optional.empty());
    when(catchEvent.getEscalationEventDefinition()).thenReturn(Optional.empty());
    when(catchEvent.getErrorEventDefinition()).thenReturn(Optional.empty());
    when(catchEvent.getTimerEventDefinition()).thenReturn(Optional.empty());
    when(catchEvent.getMessageventDefinition()).thenReturn(Optional.empty());

    processor.processStartSpecificEventInstance(
        processingContext, scope, catchEventInstance, "flow1");

    verify(catchEventInstance).setState(ExecutionState.COMPLETED);
  }

  @Test
  void processStartSpecificEventInstance_shouldCreateSignalSubscription() {
    SignalEventDefinition signalDef = mock(SignalEventDefinition.class);
    SignalEvent signal = mock(SignalEvent.class);

    when(catchEventInstance.getFlowNode()).thenReturn(catchEvent);
    when(catchEvent.getSignalEventDefinition()).thenReturn(Optional.of(signalDef));
    when(signalDef.getReferencedSignal()).thenReturn(signal);
    when(signal.name()).thenReturn("signalName");
    when(catchEvent.getEscalationEventDefinition()).thenReturn(Optional.empty());
    when(catchEvent.getErrorEventDefinition()).thenReturn(Optional.empty());
    when(catchEvent.getTimerEventDefinition()).thenReturn(Optional.empty());
    when(catchEvent.getMessageventDefinition()).thenReturn(Optional.empty());
    when(scope.getVariableScope()).thenReturn(variableScope);
    when(feelExpressionHandler.processFeelExpression("signalName", variableScope))
        .thenReturn(new TextNode("resolvedSignalName"));
    when(processingContext.getInstanceResult()).thenReturn(instanceResult);

    processor.processStartSpecificEventInstance(
        processingContext, scope, catchEventInstance, "flow1");

    verify(catchEventInstance).setState(ExecutionState.ACTIVE);

    ArgumentCaptor<NewInstanceSignalSubscriptionInfo> captor =
        ArgumentCaptor.forClass(NewInstanceSignalSubscriptionInfo.class);
    verify(instanceResult).addNewInstanceSignalSubscription(captor.capture());

    NewInstanceSignalSubscriptionInfo info = captor.getValue();
    assertEquals("resolvedSignalName", info.name());
    assertSame(catchEventInstance, info.elementInstance());
  }

  @Test
  void processStartSpecificEventInstance_shouldCreateEscalationSubscription() {
    EscalationEventDefinition escalationDef = mock(EscalationEventDefinition.class);

    when(catchEventInstance.getFlowNode()).thenReturn(catchEvent);
    when(catchEvent.getSignalEventDefinition()).thenReturn(Optional.empty());
    when(catchEvent.getEscalationEventDefinition()).thenReturn(Optional.of(escalationDef));
    when(catchEvent.getErrorEventDefinition()).thenReturn(Optional.empty());
    when(catchEvent.getTimerEventDefinition()).thenReturn(Optional.empty());
    when(catchEvent.getMessageventDefinition()).thenReturn(Optional.empty());

    processor.processStartSpecificEventInstance(
        processingContext, scope, catchEventInstance, "flow1");

    verify(catchEventInstance).setState(ExecutionState.ACTIVE);
    verify(catchEventInstance).addEscalationSubscription(escalationDef);
  }

  @Test
  void processStartSpecificEventInstance_shouldCreateErrorSubscription() {
    ErrorEventDefinition errorDef = mock(ErrorEventDefinition.class);

    when(catchEventInstance.getFlowNode()).thenReturn(catchEvent);
    when(catchEvent.getSignalEventDefinition()).thenReturn(Optional.empty());
    when(catchEvent.getEscalationEventDefinition()).thenReturn(Optional.empty());
    when(catchEvent.getErrorEventDefinition()).thenReturn(Optional.of(errorDef));
    when(catchEvent.getTimerEventDefinition()).thenReturn(Optional.empty());
    when(catchEvent.getMessageventDefinition()).thenReturn(Optional.empty());

    processor.processStartSpecificEventInstance(
        processingContext, scope, catchEventInstance, "flow1");

    verify(catchEventInstance).setState(ExecutionState.ACTIVE);
    verify(catchEventInstance).addErrorSubscription(errorDef);
  }

  @Test
  void processStartSpecificEventInstance_shouldCreateTimerSubscription() {
    TimerEventDefinition timerDef = mock(TimerEventDefinition.class);

    when(catchEventInstance.getFlowNode()).thenReturn(catchEvent);
    when(catchEvent.getSignalEventDefinition()).thenReturn(Optional.empty());
    when(catchEvent.getEscalationEventDefinition()).thenReturn(Optional.empty());
    when(catchEvent.getErrorEventDefinition()).thenReturn(Optional.empty());
    when(catchEvent.getTimerEventDefinition()).thenReturn(Optional.of(timerDef));
    when(catchEvent.getMessageventDefinition()).thenReturn(Optional.empty());
    when(scope.getVariableScope()).thenReturn(variableScope);
    when(processingContext.getInstanceResult()).thenReturn(instanceResult);

    processor.processStartSpecificEventInstance(
        processingContext, scope, catchEventInstance, "flow1");

    verify(catchEventInstance).setState(ExecutionState.ACTIVE);

    ArgumentCaptor<ScheduledContinuationInfo> captor =
        ArgumentCaptor.forClass(ScheduledContinuationInfo.class);
    verify(instanceResult).addNewScheduledContinuation(captor.capture());

    ScheduledContinuationInfo info = captor.getValue();
    assertSame(catchEventInstance, info.catchEventInstance());
    assertSame(timerDef, info.timerEventDefinition());
  }

  @Test
  void processStartSpecificEventInstance_shouldCreateMessageSubscription() {
    MessageEventDefinition messageDef = mock(MessageEventDefinition.class);
    Message message = mock(Message.class);

    when(catchEventInstance.getFlowNode()).thenReturn(catchEvent);
    when(catchEvent.getSignalEventDefinition()).thenReturn(Optional.empty());
    when(catchEvent.getEscalationEventDefinition()).thenReturn(Optional.empty());
    when(catchEvent.getErrorEventDefinition()).thenReturn(Optional.empty());
    when(catchEvent.getTimerEventDefinition()).thenReturn(Optional.empty());
    when(catchEvent.getMessageventDefinition()).thenReturn(Optional.of(messageDef));
    when(messageDef.getReferencedMessage()).thenReturn(message);
    when(message.correlationKey()).thenReturn("correlationKeyExpr");
    when(message.name()).thenReturn("messageName");
    when(scope.getVariableScope()).thenReturn(variableScope);
    when(feelExpressionHandler.processFeelExpression("correlationKeyExpr", variableScope))
        .thenReturn(new TextNode("key123"));
    when(processingContext.getInstanceResult()).thenReturn(instanceResult);

    processor.processStartSpecificEventInstance(
        processingContext, scope, catchEventInstance, "flow1");

    verify(catchEventInstance).setState(ExecutionState.ACTIVE);

    ArgumentCaptor<NewCorrelationSubscriptionMessageEventInfo> captor =
        ArgumentCaptor.forClass(NewCorrelationSubscriptionMessageEventInfo.class);
    verify(instanceResult).addNewCorrelationSubcriptionMessageEvent(captor.capture());

    NewCorrelationSubscriptionMessageEventInfo info = captor.getValue();
    assertEquals("messageName", info.messageName());
    assertEquals("key123", info.correlationKey());
    assertSame(catchEventInstance, info.elementInstance());
  }

  // Test implementation of abstract class
  private static class TestCatchEventInstanceProcessor
      extends CatchEventInstanceProcessor<CatchEvent, CatchEventInstance<?>> {

    TestCatchEventInstanceProcessor(
        IoMappingProcessor ioMappingProcessor,
        ProcessInstanceMapper processInstanceMapper,
        FeelExpressionHandler feelExpressionHandler,
        Clock clock) {
      super(ioMappingProcessor, processInstanceMapper, feelExpressionHandler, clock);
    }

    @Override
    protected boolean shoudHandleTimerEvents() {
      return true;
    }

    @Override
    protected void processContinueSpecificCatchEventInstance(
        ProcessInstanceProcessingContext processInstanceProcessingContext,
        Scope scope,
        CatchEventInstance<?> flowNodeInstance) {
      // Test implementation
    }

    @Override
    protected boolean shouldCancel(CatchEventInstance<?> flowNodeInstance) {
      return true;
    }
  }
}
