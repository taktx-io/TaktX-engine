/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */

package io.taktx.engine.pi.processor;

import static org.junit.jupiter.api.Assertions.*;

import io.taktx.engine.pd.model.ExclusiveGateway;
import io.taktx.engine.pi.ProcessInstanceProcessingContext;
import io.taktx.engine.pi.model.ExclusiveGatewayInstance;
import io.taktx.engine.pi.model.Scope;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ExclusiveGatewayInstanceProcessorTest {

  @InjectMocks private ExclusiveGatewayInstanceProcessor processor;

  @Mock private ProcessInstanceProcessingContext processingContext;
  @Mock private Scope scope;
  @Mock private ExclusiveGatewayInstance gatewayInstance;
  @Mock private ExclusiveGateway gateway;

  @Test
  void canTriggerOutputFlows_shouldAlwaysReturnTrue() {
    boolean result = processor.canTriggerOutputFlows(gatewayInstance, scope);

    assertTrue(result);
  }

  @Test
  void processStartSpecificGatewayInstance_shouldDoNothing() {
    assertDoesNotThrow(
        () ->
            processor.processStartSpecificGatewayInstance(
                processingContext, scope, gatewayInstance, "flow1"));
  }

  @Test
  void processTerminateSpecificGatewayInstance_shouldDoNothing() {
    assertDoesNotThrow(
        () -> processor.processTerminateSpecificGatewayInstance(null, null, gatewayInstance));
  }
}
