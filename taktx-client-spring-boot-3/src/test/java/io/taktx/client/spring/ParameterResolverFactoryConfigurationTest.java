/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
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
