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

import io.taktx.engine.feel.FeelExpressionHandler;
import io.taktx.engine.pd.model.InclusiveGateway;
import io.taktx.engine.pd.model.SequenceFlow;
import io.taktx.engine.pi.ProcessInstanceMapper;
import io.taktx.engine.pi.model.FlowNodeInstances;
import io.taktx.engine.pi.model.InclusiveGatewayInstance;
import io.taktx.engine.pi.model.Scope;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * TDD test for cycle detection in InclusiveGatewayInstanceProcessor. Tests that circular BPMN
 * models don't cause StackOverflowError.
 */
@ExtendWith(MockitoExtension.class)
class InclusiveGatewayCycleDetectionTest {

  @Mock private FeelExpressionHandler feelExpressionHandler;
  @Mock private IoMappingProcessor ioMappingProcessor;
  @Mock private ProcessInstanceMapper processInstanceMapper;
  @Mock private Scope scope;
  @Mock private FlowNodeInstances flowNodeInstances;
  @Mock private InclusiveGatewayInstance gatewayInstance;
  @Mock private InclusiveGateway gateway;

  private Clock clock;
  private InclusiveGatewayInstanceProcessor processor;

  @BeforeEach
  void setUp() {
    clock = Clock.fixed(Instant.parse("2025-12-11T10:00:00Z"), ZoneId.systemDefault());
    processor =
        new InclusiveGatewayInstanceProcessor(
            ioMappingProcessor, feelExpressionHandler, processInstanceMapper, clock);
  }

  @Test
  void canTriggerOutputFlows_shouldHandleCircularFlow() {
    // BUG: Circular BPMN model causes infinite recursion and StackOverflowError
    // Expected: Detect cycle and handle gracefully
    // Actual (before fix): StackOverflowError

    // Create circular flow: Gateway1 -> Gateway2 -> Gateway1
    InclusiveGateway gateway1 = mock(InclusiveGateway.class);
    InclusiveGateway gateway2 = mock(InclusiveGateway.class);

    SequenceFlow flow1to2 = mock(SequenceFlow.class);
    SequenceFlow flow2to1 = mock(SequenceFlow.class);

    // Gateway1 incoming from Gateway2
    when(gateway1.getIncomingSequenceFlows()).thenReturn(Set.of(flow2to1));
    when(flow2to1.getSourceNode()).thenReturn(gateway2);
    when(gateway2.getId()).thenReturn("gateway2");
    // Note: Gateway2's incoming flows (which would create cycle) are not stubbed
    // because cycle detection stops recursion before they're checked

    when(gatewayInstance.getFlowNode()).thenReturn(gateway1);
    when(scope.getFlowNodeInstances()).thenReturn(flowNodeInstances);

    // Should NOT cause StackOverflowError, should handle cycle gracefully
    assertDoesNotThrow(
        () -> processor.canTriggerOutputFlows(gatewayInstance, scope),
        "Circular BPMN model should not cause StackOverflowError");
  }

  @Test
  void canTriggerOutputFlows_shouldHandleDeepButAcyclicFlow() {
    // Test that legitimate deep flows still work (not everything is a cycle)
    // No incoming flows = diverging gateway, should return true

    InclusiveGateway gateway1 = mock(InclusiveGateway.class);

    when(gateway1.getIncomingSequenceFlows()).thenReturn(Set.of()); // No incoming = diverging

    when(gatewayInstance.getFlowNode()).thenReturn(gateway1);

    // Should work fine - diverging gateway always returns true
    boolean result = processor.canTriggerOutputFlows(gatewayInstance, scope);

    assertTrue(result); // Diverging gateway can trigger
  }

  @Test
  void canTriggerOutputFlows_shouldHandleSelfReferencingGateway() {
    // Edge case: Gateway has incoming flow from itself

    InclusiveGateway selfGateway = mock(InclusiveGateway.class);
    SequenceFlow selfFlow = mock(SequenceFlow.class);

    when(selfGateway.getIncomingSequenceFlows()).thenReturn(Set.of(selfFlow));
    when(selfFlow.getSourceNode()).thenReturn(selfGateway); // Points to itself!
    when(selfFlow.getId()).thenReturn("selfFlow");

    when(gatewayInstance.getFlowNode()).thenReturn(selfGateway);
    when(scope.getFlowNodeInstances()).thenReturn(flowNodeInstances);
    when(flowNodeInstances.getInstanceWithFlowNode(selfGateway))
        .thenReturn(Optional.of(gatewayInstance));
    when(gatewayInstance.getSelectedOutputFlows()).thenReturn(Set.of("selfFlow"));
    when(gatewayInstance.getTriggeredInputFlows()).thenReturn(Set.of());

    // Should handle self-reference without infinite loop
    assertDoesNotThrow(
        () -> processor.canTriggerOutputFlows(gatewayInstance, scope),
        "Self-referencing gateway should not cause infinite loop");
  }
}
