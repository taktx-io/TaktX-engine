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
import io.taktx.engine.pd.model.Message;
import io.taktx.engine.pd.model.ReceiveTask;
import io.taktx.engine.pi.InstanceResult;
import io.taktx.engine.pi.ProcessInstanceProcessingContext;
import io.taktx.engine.pi.model.NewCorrelationSubscriptionMessageEventInfo;
import io.taktx.engine.pi.model.ReceiveTaskInstance;
import io.taktx.engine.pi.model.Scope;
import io.taktx.engine.pi.model.TerminateCorrelationSubscriptionMessageEventInfo;
import io.taktx.engine.pi.model.VariableScope;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ReceiveTaskInstanceProcessorTest {

  @InjectMocks private ReceiveTaskInstanceProcessor processor;

  @Mock private FeelExpressionHandler feelExpressionHandler;
  @Mock private ProcessInstanceProcessingContext processingContext;
  @Mock private Scope scope;
  @Mock private ReceiveTaskInstance receiveTaskInstance;
  @Mock private ReceiveTask receiveTask;
  @Mock private Message message;
  @Mock private InstanceResult instanceResult;
  @Mock private VariableScope variableScope;

  @Test
  void processStartSpecificActivityInstance_shouldCreateMessageSubscription() {
    when(receiveTaskInstance.getFlowNode()).thenReturn(receiveTask);
    when(receiveTask.getReferencedMessage()).thenReturn(message);
    when(message.correlationKey()).thenReturn("correlationKeyExpression");
    when(message.name()).thenReturn("MessageName");
    when(feelExpressionHandler.processFeelExpression("correlationKeyExpression", variableScope))
        .thenReturn(new TextNode("key123"));
    when(processingContext.getInstanceResult()).thenReturn(instanceResult);

    processor.processStartSpecificActivityInstance(
        processingContext, scope, variableScope, receiveTaskInstance, "flow1");

    verify(receiveTaskInstance).setState(ExecutionState.ACTIVE);
    verify(receiveTaskInstance).setCorrelationKey("key123");

    ArgumentCaptor<NewCorrelationSubscriptionMessageEventInfo> captor =
        ArgumentCaptor.forClass(NewCorrelationSubscriptionMessageEventInfo.class);
    verify(instanceResult).addNewCorrelationSubcriptionMessageEvent(captor.capture());

    NewCorrelationSubscriptionMessageEventInfo info = captor.getValue();
    assertEquals("MessageName", info.messageName());
    assertEquals("key123", info.correlationKey());
    assertSame(receiveTaskInstance, info.elementInstance());
  }

  @Test
  void processContinueSpecificActivityInstance_shouldCompleteAndTerminateSubscription() {
    when(receiveTaskInstance.getFlowNode()).thenReturn(receiveTask);
    when(receiveTask.getReferencedMessage()).thenReturn(message);
    when(message.name()).thenReturn("MessageName");
    when(receiveTaskInstance.getCorrelationKey()).thenReturn("key123");
    when(processingContext.getInstanceResult()).thenReturn(instanceResult);

    processor.processContinueSpecificActivityInstance(
        processingContext, scope, variableScope, receiveTaskInstance, null);

    verify(receiveTaskInstance).setState(ExecutionState.COMPLETED);

    ArgumentCaptor<TerminateCorrelationSubscriptionMessageEventInfo> captor =
        ArgumentCaptor.forClass(TerminateCorrelationSubscriptionMessageEventInfo.class);
    verify(instanceResult).addTerminateCorrelationSubscriptionMessageEvent(captor.capture());

    TerminateCorrelationSubscriptionMessageEventInfo info = captor.getValue();
    assertEquals("MessageName", info.messageName());
    assertEquals("key123", info.correlationKey());
  }

  @Test
  void processAbortSpecificActivityInstance_shouldTerminateSubscription() {
    when(receiveTaskInstance.getFlowNode()).thenReturn(receiveTask);
    when(receiveTask.getReferencedMessage()).thenReturn(message);
    when(message.name()).thenReturn("MessageName");
    when(receiveTaskInstance.getCorrelationKey()).thenReturn("key123");
    when(processingContext.getInstanceResult()).thenReturn(instanceResult);

    processor.processAbortSpecificActivityInstance(
        processingContext, scope, variableScope, receiveTaskInstance);

    ArgumentCaptor<TerminateCorrelationSubscriptionMessageEventInfo> captor =
        ArgumentCaptor.forClass(TerminateCorrelationSubscriptionMessageEventInfo.class);
    verify(instanceResult).addTerminateCorrelationSubscriptionMessageEvent(captor.capture());

    TerminateCorrelationSubscriptionMessageEventInfo info = captor.getValue();
    assertEquals("MessageName", info.messageName());
    assertEquals("key123", info.correlationKey());
  }
}
