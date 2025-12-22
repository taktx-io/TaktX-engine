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

import io.taktx.client.ProcessInstanceResponder;
import io.taktx.util.TaktPropertiesHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ProcessInstanceResponderConfigurationTest {

  private ProcessInstanceResponderConfiguration configuration;
  private TaktPropertiesHelper taktPropertiesHelper;

  @BeforeEach
  void setUp() {
    configuration = new ProcessInstanceResponderConfiguration();
    taktPropertiesHelper = mock(TaktPropertiesHelper.class);
  }

  @Test
  void testProcessInstanceResponderProduction() {
    // When
    ProcessInstanceResponder responder =
        configuration.processInstanceResponder(taktPropertiesHelper);

    // Then
    assertThat(responder).isNotNull();
  }

  @Test
  void testProcessInstanceResponderProduction_returnsNewInstance() {
    // When
    ProcessInstanceResponder responder1 =
        configuration.processInstanceResponder(taktPropertiesHelper);
    ProcessInstanceResponder responder2 =
        configuration.processInstanceResponder(taktPropertiesHelper);

    // Then
    assertThat(responder1).isNotNull();
    assertThat(responder2).isNotNull();
    // Each call creates a new instance
    assertThat(responder1).isNotSameAs(responder2);
  }
}

