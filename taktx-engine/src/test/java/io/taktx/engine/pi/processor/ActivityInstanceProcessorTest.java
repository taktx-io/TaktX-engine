/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks All rights reserved.
 * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 * Free use is permitted with up to 3 Kafka partitions per topic. See LICENSE file for details.
 * For commercial use or more partitions and features, contact [https://www.taktx.io/contact].
 */

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

import com.fasterxml.jackson.databind.node.IntNode;
import com.fasterxml.jackson.databind.node.TextNode;
import io.taktx.dto.ContinueFlowElementTriggerDTO;
import io.taktx.dto.ExecutionState;
import io.taktx.engine.feel.FeelExpressionHandler;
import io.taktx.engine.pd.model.Activity;
import io.taktx.engine.pd.model.LoopCharacteristics;
import io.taktx.engine.pd.model.SequenceFlow;
import io.taktx.engine.pi.ProcessInstanceMapper;
import io.taktx.engine.pi.ProcessInstanceProcessingContext;
import io.taktx.engine.pi.model.ActivityInstance;
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
class ActivityInstanceProcessorTest {

  @Mock private FeelExpressionHandler feelExpressionHandler;
  @Mock private IoMappingProcessor ioMappingProcessor;
  @Mock private ProcessInstanceMapper processInstanceMapper;
  @Mock private ProcessInstanceProcessingContext processingContext;
  @Mock private Scope scope;
  @Mock private ActivityInstance<Activity> activityInstance;
  @Mock private Activity activity;
  @Mock private VariableScope variableScope;

  private Clock clock;
  private TestActivityInstanceProcessor processor;

  @BeforeEach
  void setUp() {
    clock = Clock.fixed(Instant.parse("2025-01-01T10:00:00Z"), ZoneId.systemDefault());
    processor =
        new TestActivityInstanceProcessor(
            feelExpressionHandler, ioMappingProcessor, processInstanceMapper, clock);
  }

  @Test
  void processStartSpecificFlowNodeInstance_shouldSetLoopVariablesForIteration() {
    LoopCharacteristics loopCharacteristics = mock(LoopCharacteristics.class);
    when(activityInstance.isIteration()).thenReturn(true);
    when(activityInstance.getLoopCnt()).thenReturn(5);
    when(activityInstance.getInputElement()).thenReturn(new TextNode("testElement"));
    when(activityInstance.getFlowNode()).thenReturn(activity);
    when(activity.getLoopCharacteristics()).thenReturn(loopCharacteristics);
    when(loopCharacteristics.getInputElement()).thenReturn("inputVar");
    when(scope.getVariableScope()).thenReturn(variableScope);
    when(activityInstance.getState()).thenReturn(ExecutionState.COMPLETED);

    processor.processStartSpecificFlowNodeInstance(
        processingContext, scope, activityInstance, "flow1");

    verify(variableScope).put("loopCnt", new IntNode(5));
    verify(variableScope).put("inputVar", new TextNode("testElement"));
  }

  @Test
  void processStartSpecificFlowNodeInstance_shouldNotSetLoopVariablesWhenNotIteration() {
    when(activityInstance.isIteration()).thenReturn(false);
    when(scope.getVariableScope()).thenReturn(variableScope);

    processor.processStartSpecificFlowNodeInstance(
        processingContext, scope, activityInstance, "flow1");

    verify(variableScope, never()).put(eq("loopCnt"), any());
  }

  @Test
  void processContinueSpecificFlowNodeInstance_shouldHandleFinishedIteration() {
    ContinueFlowElementTriggerDTO trigger = mock(ContinueFlowElementTriggerDTO.class);
    when(activityInstance.getFlowNode()).thenReturn(activity);
    when(activityInstance.getState()).thenReturn(ExecutionState.COMPLETED);
    when(activityInstance.isIteration()).thenReturn(true);
    when(scope.getVariableScope()).thenReturn(variableScope);

    LoopCharacteristics loopCharacteristics = mock(LoopCharacteristics.class);
    when(activity.getLoopCharacteristics()).thenReturn(loopCharacteristics);
    when(loopCharacteristics.getOutputElement()).thenReturn("outputVar");
    when(feelExpressionHandler.processFeelExpression("outputVar", variableScope))
        .thenReturn(new TextNode("outputValue"));

    processor.processContinueSpecificFlowNodeInstance(
        processingContext, scope, activityInstance, trigger);

    verify(activityInstance).setOutputElement(any());
  }

  @Test
  void getSelectedSequenceFlows_shouldReturnEmptySetForIteration() {
    when(activityInstance.isIteration()).thenReturn(true);

    Set<SequenceFlow> result =
        processor.getSelectedSequenceFlows(mock(ProcessInstance.class), activityInstance, scope);

    assertEquals(0, result.size());
  }

  @Test
  void getSelectedSequenceFlows_shouldReturnOutgoingFlowsWhenNotIteration() {
    SequenceFlow flow1 = mock(SequenceFlow.class);
    SequenceFlow flow2 = mock(SequenceFlow.class);
    when(activityInstance.isIteration()).thenReturn(false);
    when(activityInstance.getFlowNode()).thenReturn(activity);
    when(activity.getOutGoingSequenceFlows()).thenReturn(Set.of(flow1, flow2));

    Set<SequenceFlow> result =
        processor.getSelectedSequenceFlows(mock(ProcessInstance.class), activityInstance, scope);

    assertEquals(2, result.size());
    assertTrue(result.contains(flow1));
    assertTrue(result.contains(flow2));
  }

  // Test implementation of abstract class
  private static class TestActivityInstanceProcessor
      extends ActivityInstanceProcessor<
          Activity, ActivityInstance<Activity>, ContinueFlowElementTriggerDTO> {

    TestActivityInstanceProcessor(
        FeelExpressionHandler feelExpressionHandler,
        IoMappingProcessor ioMappingProcessor,
        ProcessInstanceMapper processInstanceMapper,
        Clock clock) {
      super(feelExpressionHandler, ioMappingProcessor, processInstanceMapper, clock);
    }

    @Override
    protected void processStartSpecificActivityInstance(
        ProcessInstanceProcessingContext processInstanceProcessingContext,
        Scope scope,
        ActivityInstance<Activity> flownodeInstance,
        String inputFlowId) {
      // Test implementation
    }

    @Override
    protected void processContinueSpecificActivityInstance(
        ProcessInstanceProcessingContext processInstanceProcessingContext,
        Scope scope,
        ActivityInstance<Activity> externalTaskInstance,
        ContinueFlowElementTriggerDTO trigger) {
      // Test implementation
    }

    @Override
    protected void processAbortSpecificActivityInstance(
        ProcessInstanceProcessingContext processInstanceProcessingContext,
        Scope scope,
        ActivityInstance<Activity> instance) {
      // Test implementation
    }
  }
}
