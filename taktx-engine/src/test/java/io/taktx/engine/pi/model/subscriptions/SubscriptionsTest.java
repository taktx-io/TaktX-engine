/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */

package io.taktx.engine.pi.model.subscriptions;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import io.taktx.dto.ExecutionState;
import io.taktx.dto.subscriptions.SubScriptionType;
import io.taktx.engine.pd.model.EventSignal;
import io.taktx.engine.pd.model.FlowElements;
import io.taktx.engine.pd.model.FlowNode;
import io.taktx.engine.pi.DirectInstanceResult;
import io.taktx.engine.pi.ProcessInstanceProcessingContext;
import io.taktx.engine.pi.model.FlowNodeInstance;
import io.taktx.engine.pi.model.FlowNodeInstances;
import io.taktx.engine.pi.model.Scope;
import io.taktx.engine.pi.model.VariableScope;
import io.taktx.engine.pi.model.WithScope;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SubscriptionsTest {

  @Mock private ProcessInstanceProcessingContext processingContext;
  @Mock private Scope scope;
  @Mock private VariableScope variableScope;
  @Mock private FlowNodeInstance<?> flowNodeInstance;
  @Mock private EventSignal eventSignal;
  @Mock private FlowElements flowElements;
  @Mock private FlowNodeInstances flowNodeInstances;
  @Mock private DirectInstanceResult directInstanceResult;

  private Subscriptions subscriptions;

  @BeforeEach
  void setUp() {
    subscriptions = new Subscriptions();
  }

  @Test
  void cancelSubscriptionsForInstance_shouldCancelAllSubscriptionsForInstance() {
    long elementInstanceId = 42L;
    Subscription subscription1 = mock(Subscription.class);
    Subscription subscription2 = mock(Subscription.class);

    when(flowNodeInstance.getElementInstanceId()).thenReturn(elementInstanceId);

    subscriptions
        .getInstanceSubscriptions()
        .put(elementInstanceId, List.of(subscription1, subscription2));

    subscriptions.cancelSubscriptionsForInstance(processingContext, flowNodeInstance, scope);

    verify(subscription1).cancel(processingContext, scope, flowNodeInstance);
    verify(subscription2).cancel(processingContext, scope, flowNodeInstance);
  }

  @Test
  void cancelSubscriptionsForInstance_shouldNotFailWhenNoSubscriptionsExist() {
    long elementInstanceId = 42L;
    when(flowNodeInstance.getElementInstanceId()).thenReturn(elementInstanceId);

    assertDoesNotThrow(
        () ->
            subscriptions.cancelSubscriptionsForInstance(
                processingContext, flowNodeInstance, scope));
  }

  @Test
  void terminateAllSubscriptionsIfDone_shouldCancelAllSubscriptions_whenScopeIsDone() {
    Subscription subscription1 = mock(Subscription.class);
    Subscription subscription2 = mock(Subscription.class);

    when(scope.getState()).thenReturn(ExecutionState.COMPLETED);

    subscriptions.getInstanceSubscriptions().put(1L, List.of(subscription1));
    subscriptions.getInstanceSubscriptions().put(2L, List.of(subscription2));

    subscriptions.terminateAllSubscriptionsIfDone(processingContext, scope);

    verify(subscription1).cancel(processingContext, scope, null);
    verify(subscription2).cancel(processingContext, scope, null);
  }

  @Test
  void terminateAllSubscriptionsIfDone_shouldNotCancelSubscriptions_whenScopeIsNotDone() {
    Subscription subscription = mock(Subscription.class);

    when(scope.getState()).thenReturn(ExecutionState.ACTIVE);

    subscriptions.getInstanceSubscriptions().put(1L, List.of(subscription));

    subscriptions.terminateAllSubscriptionsIfDone(processingContext, scope);

    verify(subscription, never()).cancel(any(), any(), any());
  }

  @Test
  void processEvent_shouldReturnFalse_whenNoMatchingSubscription() {
    when(flowNodeInstance.getElementInstanceId()).thenReturn(99L);

    boolean result =
        subscriptions.processEvent(scope, variableScope, eventSignal, flowNodeInstance);

    assertFalse(result);
  }

  @Test
  void processEvent_shouldMatchSubscriptionByElementInstanceId() {
    long elementInstanceId = 42L;
    Subscription subscription = mock(Subscription.class);

    when(flowNodeInstance.getElementInstanceId()).thenReturn(elementInstanceId);
    when(subscription.matchesEvent(eventSignal)).thenReturn(true);
    when(subscription.getSubScriptionType()).thenReturn(SubScriptionType.CONTINUING);
    when(scope.getFlowNodeInstances()).thenReturn(flowNodeInstances);
    doReturn(flowNodeInstance).when(flowNodeInstances).getInstanceWithInstanceId(elementInstanceId);
    when(flowNodeInstance.createKeyPath()).thenReturn(List.of(elementInstanceId));
    when(scope.getProcessInstanceId()).thenReturn(java.util.UUID.randomUUID());
    when(variableScope.selectChildScope(flowNodeInstance)).thenReturn(variableScope);
    when(scope.getDirectInstanceResult()).thenReturn(directInstanceResult);
    when(eventSignal.getVariables()).thenReturn(io.taktx.dto.VariablesDTO.empty());

    subscriptions
        .getInstanceSubscriptions()
        .put(elementInstanceId, new ArrayList<>(List.of(subscription)));

    boolean result =
        subscriptions.processEvent(scope, variableScope, eventSignal, flowNodeInstance);

    assertTrue(result);
  }

  @Test
  void processEvent_shouldFallbackToProcessLevelSubscriptions_whenNoInstanceMatch() {
    long elementInstanceId = 42L;
    Subscription processLevelSubscription = mock(Subscription.class);
    FlowNode flowNode = mock(FlowNode.class);
    FlowNodeInstance<?> newInstance = mock(FlowNodeInstance.class);
    WithScope parentInstance = mock(WithScope.class);

    when(flowNodeInstance.getElementInstanceId()).thenReturn(elementInstanceId);
    when(processLevelSubscription.matchesEvent(eventSignal)).thenReturn(true);
    when(processLevelSubscription.getSubScriptionType()).thenReturn(SubScriptionType.STARTING);
    when(processLevelSubscription.getElementId()).thenReturn("elementId");
    when(scope.getFlowElements()).thenReturn(flowElements);
    when(flowElements.getFlowNode("elementId")).thenReturn(Optional.of(flowNode));
    when(scope.getParentFlowNodeInstance()).thenReturn(parentInstance);
    doReturn(newInstance).when(flowNode).createAndStoreNewInstance(parentInstance, scope);
    when(variableScope.selectChildScope(newInstance)).thenReturn(variableScope);
    when(scope.getDirectInstanceResult()).thenReturn(directInstanceResult);

    // No subscription for elementInstanceId, but there's one at process level (-1L)
    subscriptions
        .getInstanceSubscriptions()
        .put(-1L, new ArrayList<>(List.of(processLevelSubscription)));

    boolean result =
        subscriptions.processEvent(scope, variableScope, eventSignal, flowNodeInstance);

    assertTrue(result);
    verify(directInstanceResult).addNewFlowNodeInstance(any());
  }

  @Test
  void processEvent_shouldRespectSubscriptionOrder() {
    long elementInstanceId = 42L;
    Subscription highPrioritySubscription = mock(Subscription.class);
    Subscription lowPrioritySubscription = mock(Subscription.class);

    when(flowNodeInstance.getElementInstanceId()).thenReturn(elementInstanceId);
    when(highPrioritySubscription.order()).thenReturn(0);
    when(lowPrioritySubscription.order()).thenReturn(10);
    when(highPrioritySubscription.matchesEvent(eventSignal)).thenReturn(true);
    when(lowPrioritySubscription.matchesEvent(eventSignal)).thenReturn(true);
    when(highPrioritySubscription.getSubScriptionType()).thenReturn(SubScriptionType.CONTINUING);
    when(scope.getFlowNodeInstances()).thenReturn(flowNodeInstances);
    doReturn(flowNodeInstance).when(flowNodeInstances).getInstanceWithInstanceId(elementInstanceId);
    when(flowNodeInstance.createKeyPath()).thenReturn(List.of(elementInstanceId));
    when(scope.getProcessInstanceId()).thenReturn(java.util.UUID.randomUUID());
    when(variableScope.selectChildScope(flowNodeInstance)).thenReturn(variableScope);
    when(scope.getDirectInstanceResult()).thenReturn(directInstanceResult);
    when(eventSignal.getVariables()).thenReturn(io.taktx.dto.VariablesDTO.empty());

    // Add low priority first, then high priority - should still match high priority first
    List<Subscription> subs = new ArrayList<>();
    subs.add(lowPrioritySubscription);
    subs.add(highPrioritySubscription);
    subscriptions.getInstanceSubscriptions().put(elementInstanceId, subs);

    boolean result =
        subscriptions.processEvent(scope, variableScope, eventSignal, flowNodeInstance);

    assertTrue(result);
    // Verify that the high priority subscription was processed (getSubScriptionType is called to
    // determine action)
    verify(highPrioritySubscription, atLeastOnce()).getSubScriptionType();
    // Verify the continue action was taken (addContinueInstance is called for CONTINUING type)
    verify(directInstanceResult).addContinueInstance(any());
  }
}
