/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks All rights reserved.
 * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 * Free use is permitted with up to 3 Kafka partitions per topic. See LICENSE file for details.
 * For commercial use or more partitions and features, contact [https://www.taktx.io/contact].
 */

package io.taktx.engine.pi.processor;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.taktx.engine.feel.FeelExpressionHandler;
import io.taktx.engine.pi.model.Scope;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/** Integration test demonstrating the nested variable path feature with realistic scenarios. */
class IoMappingProcessorIntegrationTest {

  private IoMappingProcessor processor;
  private ObjectMapper objectMapper;

  @Mock private FeelExpressionHandler feelExpressionHandler;

  @Mock private Scope scope;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
    objectMapper = new ObjectMapper();
    processor = new IoMappingProcessor(feelExpressionHandler, objectMapper);
  }
}
