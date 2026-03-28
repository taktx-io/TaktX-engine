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
import io.taktx.engine.pd.model.EndEvent;
import io.taktx.engine.pi.ProcessInstanceProcessingContext;
import io.taktx.engine.pi.model.EndEventInstance;
import io.taktx.engine.pi.model.Scope;
import io.taktx.engine.pi.model.VariableScope;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EndEventInstanceProcessorTest {

  @InjectMocks private EndEventInstanceProcessor processor;

  @Mock private ProcessInstanceProcessingContext processingContext;
  @Mock private Scope scope;
  @Mock private VariableScope variableScope;
  @Mock private EndEventInstance endEventInstance;
  @Mock private EndEvent endEvent;

  @Test
  void processAbortSpecificFlowNodeInstance_shouldDoNothing() {
    assertDoesNotThrow(
        () ->
            processor.processAbortSpecificFlowNodeInstance(
                processingContext, scope, variableScope, endEventInstance));
  }

  @Test
  void processStartSpecificThrowEventInstance_shouldCompleteEndEvent() {
    processor.processStartSpecificThrowEventInstance(processingContext, scope, endEventInstance);

    verify(endEventInstance).setState(ExecutionState.COMPLETED);
  }
}
