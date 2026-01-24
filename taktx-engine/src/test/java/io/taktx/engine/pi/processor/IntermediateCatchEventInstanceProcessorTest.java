/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks All rights reserved.
 * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 * Free use is permitted with up to 3 Kafka partitions per topic. See LICENSE file for details.
 * For commercial use or more partitions and features, contact [https://www.taktx.io/contact].
 */

package io.taktx.engine.pi.processor;

import static org.mockito.Mockito.*;

import io.taktx.dto.ExecutionState;
import io.taktx.engine.feel.FeelExpressionHandler;
import io.taktx.engine.pd.model.IntermediateCatchEvent;
import io.taktx.engine.pi.ProcessInstanceMapper;
import io.taktx.engine.pi.ProcessInstanceProcessingContext;
import io.taktx.engine.pi.model.IntermediateCatchEventInstance;
import io.taktx.engine.pi.model.Scope;
import io.taktx.engine.pi.model.VariableScope;
import io.taktx.engine.pi.model.subscriptions.Subscriptions;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class IntermediateCatchEventInstanceProcessorTest {

  @Mock private IoMappingProcessor ioMappingProcessor;
  @Mock private FeelExpressionHandler feelExpressionHandler;
  @Mock private ProcessInstanceMapper processInstanceMapper;
  @Mock private ProcessInstanceProcessingContext processingContext;
  @Mock private Scope scope;
  @Mock private VariableScope variableScope;
  @Mock private IntermediateCatchEventInstance catchEventInstance;
  @Mock private IntermediateCatchEvent catchEvent;
  @Mock private Subscriptions subscriptions;

  private Clock clock;
  private IntermediateCatchEventInstanceProcessor processor;

  @BeforeEach
  void setUp() {
    clock = Clock.fixed(Instant.parse("2025-01-01T10:00:00Z"), ZoneId.systemDefault());
    processor =
        new IntermediateCatchEventInstanceProcessor(
            ioMappingProcessor, feelExpressionHandler, processInstanceMapper, clock);
  }

  @Test
  void processStart_shouldSetStateToActive() {
    when(catchEventInstance.getFlowNode()).thenReturn(catchEvent);
    when(catchEvent.getEventDefinitions()).thenReturn(Set.of());
    when(scope.getSubscriptions()).thenReturn(subscriptions);

    processor.processStartSpecificCatchEventInstance(
        processingContext, scope, variableScope, catchEventInstance);

    verify(catchEventInstance).setState(ExecutionState.ACTIVE);
  }

  @Test
  void processStart_shouldStartSubscriptions() {
    when(catchEventInstance.getFlowNode()).thenReturn(catchEvent);
    when(catchEvent.getEventDefinitions()).thenReturn(Set.of());
    when(scope.getSubscriptions()).thenReturn(subscriptions);

    processor.processStartSpecificCatchEventInstance(
        processingContext, scope, variableScope, catchEventInstance);

    verify(subscriptions)
        .startSubscriptionsForIntermediateCatchEventInstance(
            processingContext, variableScope, feelExpressionHandler, catchEventInstance);
  }

  @Test
  void processContinue_shouldSetStateToCompleted() {
    processor.processContinueSpecificCatchEventInstance(
        processingContext, scope, catchEventInstance);

    verify(catchEventInstance).setState(ExecutionState.COMPLETED);
  }
}
