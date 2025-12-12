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

import io.taktx.dto.FlowConditionDTO;
import io.taktx.engine.feel.FeelExpressionHandler;
import io.taktx.engine.pd.model.Gateway;
import io.taktx.engine.pd.model.SequenceFlow;
import io.taktx.engine.pi.ProcessInstanceMapper;
import io.taktx.engine.pi.model.GatewayInstance;
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

/**
 * TDD tests for FEEL expression null handling in Gateway processors. These tests document the bug
 * and should fail initially, then pass after fix.
 */
@ExtendWith(MockitoExtension.class)
class GatewayFeelNullHandlingTest {

  @Mock private IoMappingProcessor ioMappingProcessor;
  @Mock private FeelExpressionHandler feelExpressionHandler;
  @Mock private ProcessInstanceMapper processInstanceMapper;
  @Mock private ProcessInstance processInstance;
  @Mock private Scope scope;
  @Mock private GatewayInstance<Gateway> gatewayInstance;
  @Mock private Gateway gateway;
  @Mock private VariableScope variableScope;

  private Clock clock;
  private TestGatewayProcessor processor;

  @BeforeEach
  void setUp() {
    clock = Clock.fixed(Instant.parse("2025-12-11T10:00:00Z"), ZoneId.systemDefault());
    processor =
        new TestGatewayProcessor(
            ioMappingProcessor, feelExpressionHandler, processInstanceMapper, clock);
  }

  @Test
  void getSelectedSequenceFlows_shouldHandleNullFeelExpressionResult() {
    // This test documents the bug: FEEL expressions can return null
    // Expected: Flow should be skipped (not selected)
    // Actual (before fix): NullPointerException when calling .asBoolean()

    SequenceFlow flow1 = mock(SequenceFlow.class);
    SequenceFlow flow2 = mock(SequenceFlow.class);
    FlowConditionDTO condition1 = new FlowConditionDTO("invalidExpression");
    FlowConditionDTO condition2 = new FlowConditionDTO("validExpression");

    when(gatewayInstance.getFlowNode()).thenReturn(gateway);
    when(gateway.getOutGoingSequenceFlows()).thenReturn(Set.of(flow1, flow2));
    when(flow1.getCondition()).thenReturn(condition1);
    when(flow2.getCondition()).thenReturn(condition2);
    when(scope.getVariableScope()).thenReturn(variableScope);

    // Simulate FEEL expression returning null (e.g., missing variable)
    when(feelExpressionHandler.processFeelExpression("invalidExpression", variableScope))
        .thenReturn(null);
    when(feelExpressionHandler.processFeelExpression("validExpression", variableScope))
        .thenReturn(com.fasterxml.jackson.databind.node.BooleanNode.TRUE);

    processor.setCanTrigger(true);

    // Should NOT throw NPE, should skip flow1 and select flow2
    Set<SequenceFlow> result =
        processor.getSelectedSequenceFlows(processInstance, gatewayInstance, scope);

    assertEquals(1, result.size());
    assertTrue(result.contains(flow2));
    assertFalse(result.contains(flow1));
  }

  @Test
  void getSelectedSequenceFlows_shouldHandleAllNullExpressions() {
    // When all FEEL expressions return null, should use default flow or raise incident

    SequenceFlow flow1 = mock(SequenceFlow.class);
    SequenceFlow defaultFlow = mock(SequenceFlow.class);
    FlowConditionDTO condition1 = new FlowConditionDTO("badExpression");

    when(gatewayInstance.getFlowNode()).thenReturn(gateway);
    when(gateway.getOutGoingSequenceFlows()).thenReturn(Set.of(flow1, defaultFlow));
    when(gateway.getDefaultFlow()).thenReturn("defaultFlowId");
    when(gateway.getDefaultSequenceFlow()).thenReturn(defaultFlow);
    when(flow1.getCondition()).thenReturn(condition1);
    when(defaultFlow.getCondition()).thenReturn(FlowConditionDTO.NONE);
    when(scope.getVariableScope()).thenReturn(variableScope);

    // All conditions return null
    when(feelExpressionHandler.processFeelExpression("badExpression", variableScope))
        .thenReturn(null);

    processor.setCanTrigger(true);

    // Should select default flow instead of crashing
    Set<SequenceFlow> result =
        processor.getSelectedSequenceFlows(processInstance, gatewayInstance, scope);

    assertEquals(1, result.size());
    assertTrue(result.contains(defaultFlow));
  }

  @Test
  void getSelectedSequenceFlows_shouldRaiseIncidentWhenNoFlowsAndNoDefault() {
    // When all expressions are null and no default flow, should raise incident gracefully

    SequenceFlow flow1 = mock(SequenceFlow.class);
    FlowConditionDTO condition1 = new FlowConditionDTO("badExpression");

    when(gatewayInstance.getFlowNode()).thenReturn(gateway);
    when(gateway.getOutGoingSequenceFlows()).thenReturn(Set.of(flow1));
    when(gateway.getDefaultFlow()).thenReturn(null);
    when(gateway.getId()).thenReturn("gateway1");
    when(flow1.getCondition()).thenReturn(condition1);
    when(scope.getVariableScope()).thenReturn(variableScope);

    when(feelExpressionHandler.processFeelExpression("badExpression", variableScope))
        .thenReturn(null);

    processor.setCanTrigger(true);

    Set<SequenceFlow> result =
        processor.getSelectedSequenceFlows(processInstance, gatewayInstance, scope);

    assertEquals(0, result.size());
    verify(gatewayInstance).raiseIncident("No outgoing sequence flow selected for gateway");
  }

  // Test implementation of GatewayInstanceProcessor for testing
  private static class TestGatewayProcessor
      extends GatewayInstanceProcessor<
          Gateway, GatewayInstance<Gateway>, io.taktx.dto.ContinueFlowElementTriggerDTO> {

    private boolean canTrigger = false;

    TestGatewayProcessor(
        IoMappingProcessor ioMappingProcessor,
        FeelExpressionHandler feelExpressionHandler,
        ProcessInstanceMapper processInstanceMapper,
        Clock clock) {
      super(ioMappingProcessor, feelExpressionHandler, processInstanceMapper, clock);
    }

    public void setCanTrigger(boolean canTrigger) {
      this.canTrigger = canTrigger;
    }

    @Override
    protected boolean canTriggerOutputFlows(GatewayInstance<Gateway> gatewayInstance, Scope scope) {
      return canTrigger;
    }

    @Override
    protected void processStartSpecificGatewayInstance(
        io.taktx.engine.pi.ProcessInstanceProcessingContext processInstanceProcessingContext,
        Scope scope,
        GatewayInstance<Gateway> flownodeInstance,
        String inputFlowId) {}

    @Override
    protected void processTerminateSpecificGatewayInstance(
        io.taktx.engine.pi.InstanceResult instanceResult,
        io.taktx.engine.pi.DirectInstanceResult directInstanceResult,
        GatewayInstance<Gateway> instance) {}
  }
}
