/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks All rights reserved.
 * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 * Free use is permitted with up to 3 Kafka partitions per topic. See LICENSE file for details.
 * For commercial use or more partitions and features, contact [https://www.taktx.io/contact].
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
