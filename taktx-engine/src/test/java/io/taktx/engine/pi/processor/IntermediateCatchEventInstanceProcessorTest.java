/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks All rights reserved.
 * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 * Free use is permitted with up to 3 Kafka partitions per topic. See LICENSE file for details.
 * For commercial use or more partitions and features, contact [https://www.taktx.io/contact].
 */

package io.taktx.engine.pi.processor;

import static org.junit.jupiter.api.Assertions.*;

import io.taktx.engine.pi.ProcessInstanceProcessingContext;
import io.taktx.engine.pi.model.IntermediateCatchEventInstance;
import io.taktx.engine.pi.model.Scope;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class IntermediateCatchEventInstanceProcessorTest {

  @InjectMocks private IntermediateCatchEventInstanceProcessor processor;

  @Mock private ProcessInstanceProcessingContext processingContext;
  @Mock private Scope scope;
  @Mock private IntermediateCatchEventInstance intermediateCatchEventInstance;

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
                processingContext, scope, intermediateCatchEventInstance));
  }

  @Test
  void shouldCancel_shouldAlwaysReturnTrue() {
    boolean result = processor.shouldCancel(intermediateCatchEventInstance);

    assertTrue(result);
  }
}
