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

import com.fasterxml.jackson.databind.node.BooleanNode;
import io.taktx.dto.ContinueFlowElementTriggerDTO;
import io.taktx.dto.FlowConditionDTO;
import io.taktx.engine.feel.FeelExpressionHandler;
import io.taktx.engine.pd.model.Gateway;
import io.taktx.engine.pd.model.SequenceFlow;
import io.taktx.engine.pi.DirectInstanceResult;
import io.taktx.engine.pi.InstanceResult;
import io.taktx.engine.pi.ProcessInstanceMapper;
import io.taktx.engine.pi.ProcessInstanceProcessingContext;
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

@ExtendWith(MockitoExtension.class)
class GatewayInstanceProcessorTest {

  @Mock private IoMappingProcessor ioMappingProcessor;
  @Mock private FeelExpressionHandler feelExpressionHandler;
  @Mock private ProcessInstanceMapper processInstanceMapper;
  @Mock private ProcessInstanceProcessingContext processingContext;
  @Mock private Scope scope;
  @Mock private GatewayInstance<Gateway> gatewayInstance;
  @Mock private Gateway gateway;
  @Mock private VariableScope variableScope;
  @Mock private ProcessInstance processInstance;

  private Clock clock;
  private TestGatewayInstanceProcessor processor;

  @BeforeEach
  void setUp() {
    clock = Clock.fixed(Instant.parse("2025-01-01T10:00:00Z"), ZoneId.systemDefault());
    processor =
        new TestGatewayInstanceProcessor(
            ioMappingProcessor, feelExpressionHandler, processInstanceMapper, clock);
  }

  @Test
  void processContinueSpecificFlowNodeInstance_shouldThrowException() {
    ContinueFlowElementTriggerDTO trigger = mock(ContinueFlowElementTriggerDTO.class);

    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class,
            () ->
                processor.processContinueSpecificFlowNodeInstance(
                    processingContext, scope, variableScope, gatewayInstance, trigger));

    assertEquals("We should never continue a gateway instance", exception.getMessage());
  }

  @Test
  void getSelectedSequenceFlows_shouldReturnEmptySetWhenCannotTriggerOutputFlows() {
    Set<SequenceFlow> result =
        processor.getSelectedSequenceFlows(processInstance, gatewayInstance, scope, variableScope);

    assertEquals(0, result.size());
    verify(gatewayInstance).setSelectedOutputFlows(Set.of());
  }

  @Test
  void getSelectedSequenceFlows_shouldSelectFlowsWithMatchingConditions() {
    SequenceFlow flow1 = mock(SequenceFlow.class);
    SequenceFlow flow2 = mock(SequenceFlow.class);
    FlowConditionDTO condition1 = new FlowConditionDTO("x > 5");
    FlowConditionDTO condition2 = new FlowConditionDTO("y < 10");

    when(gatewayInstance.getFlowNode()).thenReturn(gateway);
    when(gateway.getOutGoingSequenceFlows()).thenReturn(Set.of(flow1, flow2));
    when(flow1.getCondition()).thenReturn(condition1);
    when(flow2.getCondition()).thenReturn(condition2);
    when(feelExpressionHandler.processFeelExpression("x > 5", variableScope))
        .thenReturn(BooleanNode.TRUE);
    when(feelExpressionHandler.processFeelExpression("y < 10", variableScope))
        .thenReturn(BooleanNode.FALSE);

    processor.setCanTrigger(true);

    Set<SequenceFlow> result =
        processor.getSelectedSequenceFlows(processInstance, gatewayInstance, scope, variableScope);

    assertEquals(1, result.size());
    assertTrue(result.contains(flow1));
    verify(gatewayInstance).resetFlows();
  }

  @Test
  void getSelectedSequenceFlows_shouldSelectDefaultFlowWhenNoConditionsMatch() {
    SequenceFlow flow1 = mock(SequenceFlow.class);
    SequenceFlow defaultFlow = mock(SequenceFlow.class);
    FlowConditionDTO condition1 = new FlowConditionDTO("x > 5");

    when(gatewayInstance.getFlowNode()).thenReturn(gateway);
    when(gateway.getOutGoingSequenceFlows()).thenReturn(Set.of(flow1, defaultFlow));
    when(gateway.getDefaultFlow()).thenReturn("defaultFlowId");
    when(gateway.getDefaultSequenceFlow()).thenReturn(defaultFlow);
    when(flow1.getCondition()).thenReturn(condition1);
    when(defaultFlow.getCondition()).thenReturn(FlowConditionDTO.NONE);
    when(feelExpressionHandler.processFeelExpression("x > 5", variableScope))
        .thenReturn(BooleanNode.FALSE);

    processor.setCanTrigger(true);

    Set<SequenceFlow> result =
        processor.getSelectedSequenceFlows(processInstance, gatewayInstance, scope, variableScope);

    assertEquals(1, result.size());
    assertTrue(result.contains(defaultFlow));
  }

  @Test
  void getSelectedSequenceFlows_shouldSelectAllFlowsWhenNoConditionsSet() {
    SequenceFlow flow1 = mock(SequenceFlow.class);
    SequenceFlow flow2 = mock(SequenceFlow.class);

    when(gatewayInstance.getFlowNode()).thenReturn(gateway);
    when(gateway.getOutGoingSequenceFlows()).thenReturn(Set.of(flow1, flow2));
    when(flow1.getCondition()).thenReturn(FlowConditionDTO.NONE);
    when(flow2.getCondition()).thenReturn(FlowConditionDTO.NONE);

    processor.setCanTrigger(true);

    Set<SequenceFlow> result =
        processor.getSelectedSequenceFlows(processInstance, gatewayInstance, scope, variableScope);

    assertEquals(2, result.size());
    assertTrue(result.contains(flow1));
    assertTrue(result.contains(flow2));
  }

  @Test
  void getSelectedSequenceFlows_shouldRaiseIncidentWhenNoFlowsSelected() {
    when(gatewayInstance.getFlowNode()).thenReturn(gateway);
    when(gateway.getOutGoingSequenceFlows()).thenReturn(Set.of());
    when(gateway.getId()).thenReturn("gateway1");

    processor.setCanTrigger(true);

    Set<SequenceFlow> result =
        processor.getSelectedSequenceFlows(processInstance, gatewayInstance, scope, variableScope);

    assertEquals(0, result.size());
    verify(gatewayInstance).raiseIncident("No outgoing sequence flow selected for gateway");
  }

  // Test implementation of abstract class
  private static class TestGatewayInstanceProcessor
      extends GatewayInstanceProcessor<
          Gateway, GatewayInstance<Gateway>, ContinueFlowElementTriggerDTO> {

    private boolean canTrigger = false;

    TestGatewayInstanceProcessor(
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
        ProcessInstanceProcessingContext processInstanceProcessingContext,
        Scope scope,
        GatewayInstance<Gateway> flownodeInstance,
        String inputFlowId) {
      // Test implementation
    }

    @Override
    protected void processTerminateSpecificGatewayInstance(
        InstanceResult instanceResult,
        DirectInstanceResult directInstanceResult,
        GatewayInstance<Gateway> instance) {
      // Test implementation
    }
  }
}
