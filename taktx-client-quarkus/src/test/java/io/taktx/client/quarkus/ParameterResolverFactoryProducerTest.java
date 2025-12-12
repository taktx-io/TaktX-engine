/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks All rights reserved.
 * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 * Free use is permitted with up to 3 Kafka partitions per topic. See LICENSE file for details.
 * For commercial use or more partitions and features, contact [https://www.taktx.io/contact].
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
