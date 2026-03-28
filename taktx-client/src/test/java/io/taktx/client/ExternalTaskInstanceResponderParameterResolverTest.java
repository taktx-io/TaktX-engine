/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
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
    UUID processInstanceId = UUID.randomUUID();
    List<Long> elementInstanceIdPath = List.of(1001L, 1002L);

    ExternalTaskTriggerDTO realTriggerDTO = mock(ExternalTaskTriggerDTO.class);
    when(realTriggerDTO.getProcessInstanceId()).thenReturn(processInstanceId);
    when(realTriggerDTO.getElementInstanceIdPath()).thenReturn(elementInstanceIdPath);

    // When
    resolver.resolve(realTriggerDTO);

    // Then
    verify(mockExternalTaskResponder).responderForExternalTaskTrigger(realTriggerDTO);
  }
}
