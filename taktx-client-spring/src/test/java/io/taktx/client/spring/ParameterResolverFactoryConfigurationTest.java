/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks All rights reserved.
 * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 * Free use is permitted with up to 3 Kafka partitions per topic. See LICENSE file for details.
 * For commercial use or more partitions and features, contact [https://www.taktx.io/contact].
 */

package io.taktx.client.spring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import io.taktx.client.DefaultParameterResolverFactory;
import io.taktx.client.ParameterResolverFactory;
import io.taktx.client.ProcessInstanceResponder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ParameterResolverFactoryConfigurationTest {

  private ParameterResolverFactoryConfiguration configuration;
  private ProcessInstanceResponder processInstanceResponder;

  @BeforeEach
  void setUp() {
    configuration = new ParameterResolverFactoryConfiguration();
    processInstanceResponder = mock(ProcessInstanceResponder.class);
  }

  @Test
  void testParameterResolverFactoryProduction() {
    // When
    ParameterResolverFactory factory =
        configuration.parameterResolverFactory(processInstanceResponder);

    // Then
    assertThat(factory).isNotNull();
    assertThat(factory).isInstanceOf(DefaultParameterResolverFactory.class);
  }

  @Test
  void testParameterResolverFactoryProduction_returnsNewInstance() {
    // When
    ParameterResolverFactory factory1 =
        configuration.parameterResolverFactory(processInstanceResponder);
    ParameterResolverFactory factory2 =
        configuration.parameterResolverFactory(processInstanceResponder);

    // Then
    assertThat(factory1).isNotNull();
    assertThat(factory2).isNotNull();
    // Each call creates a new instance
    assertThat(factory1).isNotSameAs(factory2);
  }
}

