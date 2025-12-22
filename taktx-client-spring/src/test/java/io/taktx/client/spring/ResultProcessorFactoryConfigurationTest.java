/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks All rights reserved.
 * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 * Free use is permitted with up to 3 Kafka partitions per topic. See LICENSE file for details.
 * For commercial use or more partitions and features, contact [https://www.taktx.io/contact].
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
