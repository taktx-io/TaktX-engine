/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks All rights reserved.
 * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 * Free use is permitted with up to 3 Kafka partitions per topic. See LICENSE file for details.
 * For commercial use or more partitions and features, contact [https://www.taktx.io/contact].
 */

package io.taktx.engine.pi.processor;

import static org.mockito.Mockito.*;

import io.taktx.dto.ContinueFlowElementTriggerDTO;
import io.taktx.dto.ExecutionState;
import io.taktx.dto.VariablesDTO;
import io.taktx.engine.feel.FeelExpressionHandler;
import io.taktx.engine.pd.model.CatchEvent;
import io.taktx.engine.pd.model.SequenceFlow;
import io.taktx.engine.pi.InstanceResult;
import io.taktx.engine.pi.ProcessInstanceMapper;
import io.taktx.engine.pi.ProcessInstanceProcessingContext;
import io.taktx.engine.pi.ProcessingStatistics;
import io.taktx.engine.pi.model.CatchEventInstance;
import io.taktx.engine.pi.model.ProcessInstance;
import io.taktx.engine.pi.model.Scope;
import io.taktx.engine.pi.model.VariableScope;
import io.taktx.engine.pi.model.subscriptions.Subscriptions;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CatchEventInstanceProcessorTest {

  @Mock private IoMappingProcessor ioMappingProcessor;
  @Mock private ProcessInstanceMapper processInstanceMapper;
  @Mock private FeelExpressionHandler feelExpressionHandler;
  @Mock private ProcessInstanceProcessingContext processingContext;
  @Mock private ProcessingStatistics processingStatistics;
  @Mock private InstanceResult instanceResult;
  @Mock private ProcessInstance processInstance;
  @Mock private Scope scope;
  @Mock private VariableScope variableScope;
  @Mock private VariableScope parentVariableScope;
  @Mock private CatchEventInstance<CatchEvent> catchEventInstance;
  @Mock private CatchEvent catchEvent;
  @Mock private Subscriptions subscriptions;

  private Clock clock;
  private TestCatchEventInstanceProcessor processor;

  @BeforeEach
  void setUp() {
    clock = Clock.fixed(Instant.parse("2025-01-01T10:00:00Z"), ZoneId.systemDefault());
    processor =
        new TestCatchEventInstanceProcessor(
            ioMappingProcessor, processInstanceMapper, feelExpressionHandler, clock);

    // Common setup for processingContext
    when(processingContext.getProcessingStatistics()).thenReturn(processingStatistics);
    when(processingContext.getInstanceResult()).thenReturn(instanceResult);
    when(processingContext.getProcessInstance()).thenReturn(processInstance);
    when(variableScope.getParentScope()).thenReturn(parentVariableScope);
  }

  @Test
  void processContinue_shouldCancelSubscriptions_whenInstanceIsDone() {
    ContinueFlowElementTriggerDTO trigger = mock(ContinueFlowElementTriggerDTO.class);
    when(trigger.getVariables()).thenReturn(VariablesDTO.empty());
    when(catchEventInstance.stateAllowsContinue()).thenReturn(true);
    when(catchEventInstance.isDone()).thenReturn(true);
    when(catchEventInstance.getFlowNode()).thenReturn(catchEvent);
    when(scope.getSubscriptions()).thenReturn(subscriptions);

    processor.processContinue(processingContext, scope, variableScope, catchEventInstance, trigger);

    verify(subscriptions)
        .cancelSubscriptionsForInstance(processingContext, catchEventInstance, scope);
  }

  @Test
  void processContinue_shouldNotCancelSubscriptions_whenInstanceIsNotDone() {
    ContinueFlowElementTriggerDTO trigger = mock(ContinueFlowElementTriggerDTO.class);
    when(trigger.getVariables()).thenReturn(VariablesDTO.empty());
    when(catchEventInstance.stateAllowsContinue()).thenReturn(true);
    when(catchEventInstance.isDone()).thenReturn(false);
    when(catchEventInstance.getFlowNode()).thenReturn(catchEvent);

    processor.processContinue(processingContext, scope, variableScope, catchEventInstance, trigger);

    verify(subscriptions, never()).cancelSubscriptionsForInstance(any(), any(), any());
  }

  @Test
  void processContinue_shouldSetStateToCompleted() {
    ContinueFlowElementTriggerDTO trigger = mock(ContinueFlowElementTriggerDTO.class);
    when(trigger.getVariables()).thenReturn(VariablesDTO.empty());
    when(catchEventInstance.stateAllowsContinue()).thenReturn(true);
    when(catchEventInstance.isDone()).thenReturn(true);
    when(catchEventInstance.getFlowNode()).thenReturn(catchEvent);
    when(scope.getSubscriptions()).thenReturn(subscriptions);

    processor.processContinue(processingContext, scope, variableScope, catchEventInstance, trigger);

    verify(catchEventInstance).setState(ExecutionState.COMPLETED);
  }

  @Test
  void processAbortSpecific_shouldCancelSubscriptions() {
    when(scope.getSubscriptions()).thenReturn(subscriptions);

    // Test the specific method directly to verify subscription cancellation behavior
    processor.processAbortSpecificFlowNodeInstance(
        processingContext, scope, variableScope, catchEventInstance);

    verify(subscriptions)
        .cancelSubscriptionsForInstance(processingContext, catchEventInstance, scope);
  }

  /**
   * Concrete test implementation of CatchEventInstanceProcessor for testing the abstract class
   * behavior.
   */
  private static class TestCatchEventInstanceProcessor
      extends CatchEventInstanceProcessor<CatchEvent, CatchEventInstance<CatchEvent>> {

    TestCatchEventInstanceProcessor(
        IoMappingProcessor ioMappingProcessor,
        ProcessInstanceMapper processInstanceMapper,
        FeelExpressionHandler feelExpressionHandler,
        Clock clock) {
      super(ioMappingProcessor, processInstanceMapper, feelExpressionHandler, clock);
    }

    @Override
    protected void processStartSpecificCatchEventInstance(
        ProcessInstanceProcessingContext processInstanceProcessingContext,
        Scope scope,
        VariableScope variableScope,
        CatchEventInstance<CatchEvent> flowNodeInstance) {
      // Test implementation - do nothing
    }

    @Override
    protected void processContinueSpecificCatchEventInstance(
        ProcessInstanceProcessingContext processInstanceProcessingContext,
        Scope scope,
        CatchEventInstance<CatchEvent> flowNodeInstance) {
      // Test implementation - do nothing
    }

    @Override
    protected Set<SequenceFlow> getSelectedSequenceFlows(
        ProcessInstance processInstance,
        CatchEventInstance<CatchEvent> flowNodeInstance,
        Scope scope,
        VariableScope variableScope) {
      return Set.of();
    }
  }
}
