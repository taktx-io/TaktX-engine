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
import io.taktx.engine.pd.model.Message;
import io.taktx.engine.pd.model.ReceiveTask;
import io.taktx.engine.pi.ProcessInstanceMapper;
import io.taktx.engine.pi.ProcessInstanceProcessingContext;
import io.taktx.engine.pi.model.ReceiveTaskInstance;
import io.taktx.engine.pi.model.Scope;
import io.taktx.engine.pi.model.VariableScope;
import io.taktx.engine.pi.model.subscriptions.Subscriptions;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ReceiveTaskInstanceProcessorTest {

  @Mock private FeelExpressionHandler feelExpressionHandler;
  @Mock private IoMappingProcessor ioMappingProcessor;
  @Mock private ProcessInstanceMapper processInstanceMapper;
  @Mock private ProcessInstanceProcessingContext processingContext;
  @Mock private Scope scope;
  @Mock private VariableScope variableScope;
  @Mock private ReceiveTaskInstance receiveTaskInstance;
  @Mock private ReceiveTask receiveTask;
  @Mock private Subscriptions subscriptions;
  @Mock private Message message;

  private Clock clock;
  private ReceiveTaskInstanceProcessor processor;

  @BeforeEach
  void setUp() {
    clock = Clock.fixed(Instant.parse("2025-01-01T10:00:00Z"), ZoneId.systemDefault());
    processor =
        new ReceiveTaskInstanceProcessor(
            feelExpressionHandler, ioMappingProcessor, processInstanceMapper, clock);
  }

  @Test
  void processStart_shouldSetStateToActive() {
    when(receiveTaskInstance.getFlowNode()).thenReturn(receiveTask);
    when(receiveTask.getReferencedMessage()).thenReturn(message);
    when(scope.getSubscriptions()).thenReturn(subscriptions);

    processor.processStartSpecificActivityInstance(
        processingContext, scope, variableScope, receiveTaskInstance, "flow1");

    verify(receiveTaskInstance).setState(ExecutionState.ACTIVE);
  }

  @Test
  void processStart_shouldStartMessageSubscription() {
    when(receiveTaskInstance.getFlowNode()).thenReturn(receiveTask);
    when(receiveTask.getReferencedMessage()).thenReturn(message);
    when(scope.getSubscriptions()).thenReturn(subscriptions);

    processor.processStartSpecificActivityInstance(
        processingContext, scope, variableScope, receiveTaskInstance, "flow1");

    verify(subscriptions)
        .startSubscriptionsForReceiveTask(
            processingContext, variableScope, feelExpressionHandler, receiveTaskInstance);
  }

  @Test
  void processContinue_shouldSetStateToCompleted() {
    ContinueFlowElementTriggerDTO trigger = mock(ContinueFlowElementTriggerDTO.class);
    when(trigger.getVariables()).thenReturn(VariablesDTO.empty());

    processor.processContinueSpecificActivityInstance(
        processingContext, scope, variableScope, receiveTaskInstance, trigger);

    verify(receiveTaskInstance).setState(ExecutionState.COMPLETED);
  }
}
