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

import io.taktx.engine.pd.model.BoundaryEvent;
import io.taktx.engine.pi.ProcessInstanceProcessingContext;
import io.taktx.engine.pi.model.BoundaryEventInstance;
import io.taktx.engine.pi.model.Scope;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BoundaryEventInstanceProcessorTest {

  @InjectMocks private BoundaryEventInstanceProcessor processor;

  @Mock private ProcessInstanceProcessingContext processingContext;
  @Mock private Scope scope;
  @Mock private BoundaryEventInstance boundaryEventInstance;
  @Mock private BoundaryEvent boundaryEvent;

  @Test
  void shoudHandleTimerEvents_shouldReturnTrue() {
    boolean result = processor.shoudHandleTimerEvents();

    assertTrue(result);
  }

  @Test
  void processContinueSpecificCatchEventInstance_shouldDoNothing() {
    assertDoesNotThrow(
        () ->
            processor.processContinueSpecificCatchEventInstance(
                processingContext, scope, boundaryEventInstance));
  }

  @Test
  void shouldCancel_shouldReturnTrueWhenCancelActivityIsTrue() {
    when(boundaryEventInstance.getFlowNode()).thenReturn(boundaryEvent);
    when(boundaryEvent.isCancelActivity()).thenReturn(true);

    boolean result = processor.shouldCancel(boundaryEventInstance);

    assertTrue(result);
  }

  @Test
  void shouldCancel_shouldReturnFalseWhenCancelActivityIsFalse() {
    when(boundaryEventInstance.getFlowNode()).thenReturn(boundaryEvent);
    when(boundaryEvent.isCancelActivity()).thenReturn(false);

    boolean result = processor.shouldCancel(boundaryEventInstance);

    assertFalse(result);
  }
}
