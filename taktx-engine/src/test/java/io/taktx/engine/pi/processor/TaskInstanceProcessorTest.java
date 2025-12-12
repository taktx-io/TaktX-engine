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

import io.taktx.dto.ExecutionState;
import io.taktx.engine.pd.model.Task;
import io.taktx.engine.pi.ProcessInstanceProcessingContext;
import io.taktx.engine.pi.model.Scope;
import io.taktx.engine.pi.model.TaskInstance;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TaskInstanceProcessorTest {

  @InjectMocks private TaskInstanceProcessor processor;

  @Mock private ProcessInstanceProcessingContext processingContext;
  @Mock private Scope scope;
  @Mock private TaskInstance taskInstance;
  @Mock private Task task;

  @Test
  void processStartSpecificActivityInstance_shouldCompleteTaskImmediately() {
    processor.processStartSpecificActivityInstance(processingContext, scope, taskInstance, "flow1");

    verify(taskInstance).setState(ExecutionState.COMPLETED);
  }

  @Test
  void processContinueSpecificActivityInstance_shouldDoNothing() {
    assertDoesNotThrow(
        () ->
            processor.processContinueSpecificActivityInstance(
                processingContext, scope, taskInstance, null));
  }

  @Test
  void processAbortSpecificActivityInstance_shouldDoNothing() {
    assertDoesNotThrow(
        () ->
            processor.processAbortSpecificActivityInstance(processingContext, scope, taskInstance));
  }
}
