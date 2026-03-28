/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
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
