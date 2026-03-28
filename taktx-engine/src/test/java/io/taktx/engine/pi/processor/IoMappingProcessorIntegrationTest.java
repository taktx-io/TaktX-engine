/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
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
