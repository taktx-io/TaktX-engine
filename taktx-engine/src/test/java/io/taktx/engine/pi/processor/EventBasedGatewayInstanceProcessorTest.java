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

import io.taktx.dto.ContinueFlowElementTriggerDTO;
import io.taktx.engine.feel.FeelExpressionHandler;
import io.taktx.engine.pd.model.EventBasedGateway;
import io.taktx.engine.pd.model.SequenceFlow;
import io.taktx.engine.pi.DirectInstanceResult;
import io.taktx.engine.pi.ProcessInstanceMapper;
import io.taktx.engine.pi.ProcessInstanceProcessingContext;
import io.taktx.engine.pi.model.EventBasedGatewayInstance;
import io.taktx.engine.pi.model.ProcessInstance;
import io.taktx.engine.pi.model.Scope;
import io.taktx.engine.pi.model.VariableScope;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EventBasedGatewayInstanceProcessorTest {

  @Mock private IoMappingProcessor ioMappingProcessor;
  @Mock private FeelExpressionHandler feelExpressionHandler;
  @Mock private ProcessInstanceMapper processInstanceMapper;
  @Mock private ProcessInstanceProcessingContext processingContext;
  @Mock private ProcessInstance processInstance;
  @Mock private Scope scope;
  @Mock private VariableScope variableScope;
  @Mock private EventBasedGatewayInstance gatewayInstance;
  @Mock private EventBasedGateway eventBasedGateway;
  @Mock private DirectInstanceResult directInstanceResult;

  private Clock clock;
  private EventBasedGatewayInstanceProcessor processor;

  @BeforeEach
  void setUp() {
    clock = Clock.fixed(Instant.parse("2025-01-01T10:00:00Z"), ZoneId.systemDefault());
    processor =
        new EventBasedGatewayInstanceProcessor(
            ioMappingProcessor, feelExpressionHandler, processInstanceMapper, clock);
  }

  @Test
  void canTriggerOutputFlows_shouldAlwaysReturnTrue() {
    boolean result = processor.canTriggerOutputFlows(gatewayInstance, scope);

    assertTrue(result);
  }

  @Test
  void getSelectedSequenceFlows_shouldReturnAllOutgoingFlows() {
    SequenceFlow flow1 = mock(SequenceFlow.class);
    SequenceFlow flow2 = mock(SequenceFlow.class);
    Set<SequenceFlow> outgoingFlows = Set.of(flow1, flow2);

    when(gatewayInstance.getFlowNode()).thenReturn(eventBasedGateway);
    when(eventBasedGateway.getOutGoingSequenceFlows()).thenReturn(outgoingFlows);

    Set<SequenceFlow> result =
        processor.getSelectedSequenceFlows(processInstance, gatewayInstance, scope, variableScope);

    assertEquals(outgoingFlows, result);
  }

  @Test
  void processContinue_shouldThrowIllegalStateException() {
    ContinueFlowElementTriggerDTO trigger = mock(ContinueFlowElementTriggerDTO.class);

    assertThrows(
        IllegalStateException.class,
        () ->
            processor.processContinueSpecificFlowNodeInstance(
                processingContext, scope, variableScope, gatewayInstance, trigger));
  }
}
