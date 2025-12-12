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

import io.taktx.engine.pd.model.InclusiveGateway;
import io.taktx.engine.pd.model.SequenceFlow;
import io.taktx.engine.pi.ProcessInstanceProcessingContext;
import io.taktx.engine.pi.model.FlowNodeInstances;
import io.taktx.engine.pi.model.InclusiveGatewayInstance;
import io.taktx.engine.pi.model.Scope;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class InclusiveGatewayInstanceProcessorTest {

  @InjectMocks private InclusiveGatewayInstanceProcessor processor;

  @Mock private ProcessInstanceProcessingContext processingContext;
  @Mock private Scope scope;
  @Mock private InclusiveGatewayInstance gatewayInstance;
  @Mock private InclusiveGateway gateway;
  @Mock private FlowNodeInstances flowNodeInstances;

  @Test
  void processStartSpecificGatewayInstance_shouldAddTriggeredInputFlow() {
    processor.processStartSpecificGatewayInstance(
        processingContext, scope, gatewayInstance, "flow1");

    verify(gatewayInstance).addTriggeredInputFlow("flow1");
  }

  @Test
  void processTerminateSpecificGatewayInstance_shouldDoNothing() {
    assertDoesNotThrow(
        () -> processor.processTerminateSpecificGatewayInstance(null, null, gatewayInstance));
  }

  @Test
  void canTriggerOutputFlows_shouldReturnTrueWhenNoPreviousGateways() {
    when(gatewayInstance.getFlowNode()).thenReturn(gateway);
    when(gateway.getIncomingSequenceFlows()).thenReturn(Set.of());

    boolean result = processor.canTriggerOutputFlows(gatewayInstance, scope);

    assertTrue(result);
  }

  @Test
  void canTriggerOutputFlows_shouldReturnTrueWhenAllCorrespondingFlowsTriggered() {
    SequenceFlow incomingFlow = mock(SequenceFlow.class);
    InclusiveGateway sourceGateway = mock(InclusiveGateway.class);
    InclusiveGatewayInstance sourceGatewayInstance = mock(InclusiveGatewayInstance.class);

    when(gatewayInstance.getFlowNode()).thenReturn(gateway);
    when(gateway.getIncomingSequenceFlows()).thenReturn(Set.of(incomingFlow));
    when(incomingFlow.getSourceNode()).thenReturn(sourceGateway);
    when(incomingFlow.getId()).thenReturn("flow1");
    when(scope.getFlowNodeInstances()).thenReturn(flowNodeInstances);
    when(flowNodeInstances.getInstanceWithFlowNode(sourceGateway))
        .thenReturn(java.util.Optional.of(sourceGatewayInstance));
    when(sourceGatewayInstance.getSelectedOutputFlows()).thenReturn(Set.of("flow1"));
    when(gatewayInstance.getTriggeredInputFlows()).thenReturn(Set.of("flow1"));

    boolean result = processor.canTriggerOutputFlows(gatewayInstance, scope);

    assertTrue(result);
  }

  @Test
  void canTriggerOutputFlows_shouldReturnFalseWhenNotAllFlowsTriggered() {
    SequenceFlow incomingFlow = mock(SequenceFlow.class);
    InclusiveGateway sourceGateway = mock(InclusiveGateway.class);
    InclusiveGatewayInstance sourceGatewayInstance = mock(InclusiveGatewayInstance.class);

    when(gatewayInstance.getFlowNode()).thenReturn(gateway);
    when(gateway.getIncomingSequenceFlows()).thenReturn(Set.of(incomingFlow));
    when(incomingFlow.getSourceNode()).thenReturn(sourceGateway);
    when(incomingFlow.getId()).thenReturn("flow1");
    when(scope.getFlowNodeInstances()).thenReturn(flowNodeInstances);
    when(flowNodeInstances.getInstanceWithFlowNode(sourceGateway))
        .thenReturn(java.util.Optional.of(sourceGatewayInstance));
    when(sourceGatewayInstance.getSelectedOutputFlows()).thenReturn(Set.of("flow1"));
    when(gatewayInstance.getTriggeredInputFlows()).thenReturn(Set.of());

    boolean result = processor.canTriggerOutputFlows(gatewayInstance, scope);

    assertFalse(result);
  }
}
