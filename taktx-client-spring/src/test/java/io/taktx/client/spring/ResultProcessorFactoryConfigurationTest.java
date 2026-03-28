/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.client.spring;

import static org.assertj.core.api.Assertions.assertThat;

import io.taktx.client.DefaultResultProcessorFactory;
import io.taktx.client.ResultProcessorFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ResultProcessorFactoryConfigurationTest {

  private ResultProcessorFactoryConfiguration configuration;

  @BeforeEach
  void setUp() {
    configuration = new ResultProcessorFactoryConfiguration();
  }

  @Test
  void testResultProcessorFactoryProduction() {
    // When
    ResultProcessorFactory factory = configuration.resultProcessorFactory();

    // Then
    assertThat(factory).isNotNull();
    assertThat(factory).isInstanceOf(DefaultResultProcessorFactory.class);
  }

  @Test
  void testResultProcessorFactoryProduction_returnsNewInstance() {
    // When
    ResultProcessorFactory factory1 = configuration.resultProcessorFactory();
    ResultProcessorFactory factory2 = configuration.resultProcessorFactory();

    // Then
    assertThat(factory1).isNotNull();
    assertThat(factory2).isNotNull();
    // Each call creates a new instance (not a singleton pattern in the configuration itself)
    assertThat(factory1).isNotSameAs(factory2);
  }
}
