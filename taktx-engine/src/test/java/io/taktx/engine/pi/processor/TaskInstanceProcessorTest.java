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

import io.taktx.dto.ExecutionState;
import io.taktx.engine.pd.model.Task;
import io.taktx.engine.pi.ProcessInstanceProcessingContext;
import io.taktx.engine.pi.model.Scope;
import io.taktx.engine.pi.model.TaskInstance;
import io.taktx.engine.pi.model.VariableScope;
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
  @Mock private VariableScope variableScope;
  @Mock private TaskInstance taskInstance;
  @Mock private Task task;

  @Test
  void processStartSpecificActivityInstance_shouldCompleteTaskImmediately() {
    processor.processStartSpecificActivityInstance(
        processingContext, scope, variableScope, taskInstance, "flow1");

    verify(taskInstance).setState(ExecutionState.COMPLETED);
  }

  @Test
  void processContinueSpecificActivityInstance_shouldDoNothing() {
    assertDoesNotThrow(
        () ->
            processor.processContinueSpecificActivityInstance(
                processingContext, scope, variableScope, taskInstance, null));
  }

  @Test
  void processAbortSpecificActivityInstance_shouldDoNothing() {
    assertDoesNotThrow(
        () ->
            processor.processAbortSpecificActivityInstance(
                processingContext, scope, variableScope, taskInstance));
  }
}
