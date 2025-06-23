/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks All rights reserved.
 * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 * Free use is permitted with up to 3 Kafka partitions per topic. See LICENSE file for details.
 * For commercial use or more partitions and features, contact [https://www.taktx.io/contact].
 */

package io.taktx.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.taktx.dto.ExternalTaskTriggerDTO;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ExternalTaskInstanceResponderParameterResolverTest {

  private ProcessInstanceResponder mockExternalTaskResponder;
  private ExternalTaskInstanceResponderParameterResolver resolver;
  private ExternalTaskInstanceResponder mockResponder;
  private ExternalTaskTriggerDTO mockTriggerDTO;

  @BeforeEach
  void setUp() {
    mockExternalTaskResponder = mock(ProcessInstanceResponder.class);
    resolver = new ExternalTaskInstanceResponderParameterResolver(mockExternalTaskResponder);

    mockResponder = mock(ExternalTaskInstanceResponder.class);
    mockTriggerDTO = mock(ExternalTaskTriggerDTO.class);

    // Set up the mock to return our mock responder
    when(mockExternalTaskResponder.responderForExternalTaskTrigger(mockTriggerDTO))
        .thenReturn(mockResponder);
  }

  @Test
  void shouldResolveExternalTaskInstanceResponder() {
    // When
    Object result = resolver.resolve(mockTriggerDTO);

    // Then
    verify(mockExternalTaskResponder).responderForExternalTaskTrigger(mockTriggerDTO);
    assertThat(result).isEqualTo(mockResponder);
  }

  @Test
  void shouldCallResponderWithCorrectTrigger() {
    // Given
    UUID processInstanceKey = UUID.randomUUID();
    List<Long> elementInstanceIdPath = List.of(1001L, 1002L);

    ExternalTaskTriggerDTO realTriggerDTO = mock(ExternalTaskTriggerDTO.class);
    when(realTriggerDTO.getProcessInstanceKey()).thenReturn(processInstanceKey);
    when(realTriggerDTO.getElementInstanceIdPath()).thenReturn(elementInstanceIdPath);

    // When
    resolver.resolve(realTriggerDTO);

    // Then
    verify(mockExternalTaskResponder).responderForExternalTaskTrigger(realTriggerDTO);
  }
}
