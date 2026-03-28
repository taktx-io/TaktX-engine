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

import io.taktx.engine.pd.model.ParallelGateway;
import io.taktx.engine.pi.ProcessInstanceProcessingContext;
import io.taktx.engine.pi.model.ParallelGatewayInstance;
import io.taktx.engine.pi.model.Scope;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ParallelGatewayInstanceProcessorTest {

  @InjectMocks private ParallelGatewayInstanceProcessor processor;

  @Mock private ProcessInstanceProcessingContext processingContext;
  @Mock private Scope scope;
  @Mock private ParallelGatewayInstance gatewayInstance;
  @Mock private ParallelGateway gateway;

  @Test
  void canTriggerOutputFlows_shouldReturnTrueWhenAllIncomingFlowsTriggered() {
    when(gatewayInstance.getFlowNode()).thenReturn(gateway);
    when(gateway.getIncoming()).thenReturn(Set.of("flow1", "flow2", "flow3"));
    when(gatewayInstance.getTriggeredFlows()).thenReturn(Set.of("flow1", "flow2", "flow3"));

    boolean result = processor.canTriggerOutputFlows(gatewayInstance, scope);

    assertTrue(result);
  }

  @Test
  void canTriggerOutputFlows_shouldReturnFalseWhenNotAllIncomingFlowsTriggered() {
    when(gatewayInstance.getFlowNode()).thenReturn(gateway);
    when(gateway.getIncoming()).thenReturn(Set.of("flow1", "flow2", "flow3"));
    when(gatewayInstance.getTriggeredFlows()).thenReturn(Set.of("flow1", "flow2"));

    boolean result = processor.canTriggerOutputFlows(gatewayInstance, scope);

    assertFalse(result);
  }

  @Test
  void processStartSpecificGatewayInstance_shouldAddTriggeredFlow() {
    processor.processStartSpecificGatewayInstance(
        processingContext, scope, gatewayInstance, "flow1");

    verify(gatewayInstance).addTriggeredFlow("flow1");
  }

  @Test
  void processTerminateSpecificGatewayInstance_shouldDoNothing() {
    assertDoesNotThrow(
        () -> processor.processTerminateSpecificGatewayInstance(null, null, gatewayInstance));
  }
}
