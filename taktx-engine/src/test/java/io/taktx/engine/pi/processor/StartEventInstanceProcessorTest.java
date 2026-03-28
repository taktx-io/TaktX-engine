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
import io.taktx.engine.feel.FeelExpressionHandler;
import io.taktx.engine.pd.model.StartEvent;
import io.taktx.engine.pi.ProcessInstanceMapper;
import io.taktx.engine.pi.ProcessInstanceProcessingContext;
import io.taktx.engine.pi.model.Scope;
import io.taktx.engine.pi.model.StartEventInstance;
import io.taktx.engine.pi.model.VariableScope;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class StartEventInstanceProcessorTest {

  @Mock private IoMappingProcessor ioMappingProcessor;
  @Mock private ProcessInstanceMapper processInstanceMapper;
  @Mock private FeelExpressionHandler feelExpressionHandler;
  @Mock private ProcessInstanceProcessingContext processingContext;
  @Mock private Scope scope;
  @Mock private VariableScope variableScope;
  @Mock private StartEventInstance startEventInstance;
  @Mock private StartEvent startEvent;

  private Clock clock;
  private StartEventInstanceProcessor processor;

  @BeforeEach
  void setUp() {
    clock = Clock.fixed(Instant.parse("2025-01-01T10:00:00Z"), ZoneId.systemDefault());
    processor =
        new StartEventInstanceProcessor(
            ioMappingProcessor, processInstanceMapper, feelExpressionHandler, clock);
  }

  @Test
  void processStart_shouldSetStateToCompleted() {
    processor.processStartSpecificCatchEventInstance(
        processingContext, scope, variableScope, startEventInstance);

    verify(startEventInstance).setState(ExecutionState.COMPLETED);
  }

  @Test
  void processContinue_shouldThrowIllegalStateException() {
    assertThrows(
        IllegalStateException.class,
        () ->
            processor.processContinueSpecificCatchEventInstance(
                processingContext, scope, startEventInstance));
  }
}
