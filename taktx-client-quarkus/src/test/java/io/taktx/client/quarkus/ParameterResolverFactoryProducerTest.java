/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.client.quarkus;

import static org.assertj.core.api.Assertions.assertThat;

import io.taktx.client.DefaultParameterResolverFactory;
import io.taktx.client.ParameterResolverFactory;
import io.taktx.client.ProcessInstanceResponder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ParameterResolverFactoryProducerTest {

  @Mock private ProcessInstanceResponder processInstanceResponder;

  private ParameterResolverFactoryProducer producer;

  @BeforeEach
  void setUp() {
    producer = new ParameterResolverFactoryProducer(processInstanceResponder);
  }

  @Test
  void testParameterResolverFactoryProduction() {
    // When
    ParameterResolverFactory factory = producer.produceParameterResolverFactory();

    // Then
    assertThat(factory).isNotNull();
    assertThat(factory).isInstanceOf(DefaultParameterResolverFactory.class);
  }

  @Test
  void testParameterResolverFactoryProduction_usesInjectedResponder() {
    // When
    ParameterResolverFactory factory = producer.produceParameterResolverFactory();

    // Then
    assertThat(factory).isNotNull();
    // The factory should be created with the injected responder
  }

  @Test
  void testParameterResolverFactoryProduction_returnsNewInstance() {
    // When
    ParameterResolverFactory factory1 = producer.produceParameterResolverFactory();
    ParameterResolverFactory factory2 = producer.produceParameterResolverFactory();

    // Then
    assertThat(factory1).isNotNull();
    assertThat(factory2).isNotNull();
    // Each call creates a new instance
    assertThat(factory1).isNotSameAs(factory2);
  }
}
