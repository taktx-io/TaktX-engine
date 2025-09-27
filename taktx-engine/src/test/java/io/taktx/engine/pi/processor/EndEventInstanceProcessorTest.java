/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks All rights reserved.
 * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 * Free use is permitted with up to 3 Kafka partitions per topic. See LICENSE file for details.
 * For commercial use or more partitions and features, contact [https://www.taktx.io/contact].
 */

package io.taktx.engine.pi.processor;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import io.taktx.engine.pi.FlowNodeInstanceProcessingContext;
import io.taktx.engine.pi.ProcessInstanceProcessingContext;
import io.taktx.engine.pi.model.EndEventInstance;
import io.taktx.engine.pi.model.VariableScope;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EndEventInstanceProcessorTest {

  @InjectMocks EndEventInstanceProcessor endEventInstanceProcessor;

  @Test
  void testProcessAbortSpecificFlowNodeInstance() {
    assertDoesNotThrow(
        () ->
            endEventInstanceProcessor.processTerminateSpecificFlowNodeInstance(
                Mockito.mock(ProcessInstanceProcessingContext.class),
                Mockito.mock(FlowNodeInstanceProcessingContext.class),
                Mockito.mock(EndEventInstance.class),
                Mockito.mock(VariableScope.class)));
  }

  @Test
  void testProcessStartSpecificThrowEventInstance() {
    assertDoesNotThrow(
        () ->
            endEventInstanceProcessor.processStartSpecificThrowEventInstance(
                Mockito.mock(ProcessInstanceProcessingContext.class),
                Mockito.mock(FlowNodeInstanceProcessingContext.class),
                Mockito.mock(EndEventInstance.class),
                Mockito.mock(VariableScope.class)));
  }
}
