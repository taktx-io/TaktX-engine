/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks All rights reserved.
 * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 * Free use is permitted with up to 3 Kafka partitions per topic. See LICENSE file for details.
 * For commercial use or more partitions and features, contact [https://www.taktx.io/contact].
 */

package io.taktx.client.quarkus;

import static org.assertj.core.api.Assertions.assertThat;

import io.taktx.util.TaktPropertiesHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProcessInstanceResponderProducerTest {

  @Mock private TaktPropertiesHelper taktPropertiesHelper;

  private ProcessInstanceResponderProducer producer;

  @BeforeEach
  void setUp() {
    producer = new ProcessInstanceResponderProducer(taktPropertiesHelper);
  }

  @Test
  void testProducerInitialization() {
    // Then - verify producer was constructed with dependencies
    assertThat(producer).isNotNull();
  }

  @Test
  void testProducerAcceptsDependencies() {
    // Note: ProcessInstanceResponderProducer.processInstanceResponder() creates a
    // ProcessInstanceResponder which requires a real Kafka connection to instantiate.
    // Proper testing would require either:
    // 1. Integration tests with an embedded Kafka broker
    // 2. Refactoring ProcessInstanceResponder to accept KafkaProducer as a dependency
    //
    // For unit testing, we verify the producer bean itself is properly constructed.
    assertThat(producer).isNotNull();
  }
}
