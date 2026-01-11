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

import io.taktx.dto.ContinueFlowElementTriggerDTO;
import io.taktx.engine.pd.model.Event;
import io.taktx.engine.pd.model.SequenceFlow;
import io.taktx.engine.pi.ProcessInstanceMapper;
import io.taktx.engine.pi.ProcessInstanceProcessingContext;
import io.taktx.engine.pi.model.EventInstance;
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
class EventInstanceProcessorTest {

  @Mock private IoMappingProcessor ioMappingProcessor;
  @Mock private ProcessInstanceMapper processInstanceMapper;
  @Mock private ProcessInstanceProcessingContext processingContext;
  @Mock private Scope scope;
  @Mock private VariableScope variableScope;
  @Mock private EventInstance<?> eventInstance;
  @Mock private Event event;

  private Clock clock;
  private TestEventInstanceProcessor processor;

  @BeforeEach
  void setUp() {
    clock = Clock.fixed(Instant.parse("2025-01-01T10:00:00Z"), ZoneId.systemDefault());
    processor = new TestEventInstanceProcessor(ioMappingProcessor, processInstanceMapper, clock);
  }

  @Test
  void processContinueSpecificFlowNodeInstance_shouldThrowException() {
    ContinueFlowElementTriggerDTO trigger = mock(ContinueFlowElementTriggerDTO.class);

    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class,
            () ->
                processor.processContinueSpecificFlowNodeInstance(
                    processingContext, scope, variableScope, eventInstance, trigger));

    assertEquals("We should never continue an event instance", exception.getMessage());
  }

  @Test
  void getSelectedSequenceFlows_shouldReturnOutgoingFlows() {
    SequenceFlow flow1 = mock(SequenceFlow.class);
    SequenceFlow flow2 = mock(SequenceFlow.class);

    when(eventInstance.getFlowNode()).thenReturn(event);
    when(event.getOutGoingSequenceFlows()).thenReturn(Set.of(flow1, flow2));

    Set<SequenceFlow> result =
        processor.getSelectedSequenceFlows(
            mock(ProcessInstance.class), eventInstance, scope, variableScope);

    assertEquals(2, result.size());
    assertTrue(result.contains(flow1));
    assertTrue(result.contains(flow2));
  }

  // Test implementation of abstract class
  private static class TestEventInstanceProcessor
      extends EventInstanceProcessor<Event, EventInstance<?>> {

    TestEventInstanceProcessor(
        IoMappingProcessor ioMappingProcessor,
        ProcessInstanceMapper processInstanceMapper,
        Clock clock) {
      super(ioMappingProcessor, processInstanceMapper, clock);
    }

    @Override
    protected void processStartSpecificEventInstance(
        ProcessInstanceProcessingContext processInstanceProcessingContext,
        Scope scope,
        VariableScope variableScope,
        EventInstance<?> flowNodeInstance,
        String inputFlowId) {
      // Test implementation
    }

    @Override
    protected void processAbortSpecificFlowNodeInstance(
        ProcessInstanceProcessingContext processInstanceProcessingContext,
        Scope scope,
        VariableScope variableScope,
        EventInstance<?> instance) {
      // Test implementation
    }
  }
}
