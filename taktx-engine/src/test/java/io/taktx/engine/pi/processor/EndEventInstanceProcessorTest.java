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
