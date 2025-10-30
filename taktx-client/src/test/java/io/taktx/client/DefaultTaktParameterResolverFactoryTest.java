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

import io.taktx.client.annotation.Variable;
import io.taktx.dto.ExternalTaskTriggerDTO;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DefaultTaktParameterResolverFactoryTest {

  private DefaultTaktParameterResolverFactory factory;
  private ProcessInstanceResponder externalTaskResponder;

  @BeforeEach
  void setUp() {
    externalTaskResponder = mock(ProcessInstanceResponder.class);
    factory = new DefaultTaktParameterResolverFactory(externalTaskResponder);
  }

  @Test
  void shouldCreateExternalTaskTriggerDTOResolver() throws NoSuchMethodException {
    // Given
    Method method =
        TestService.class.getDeclaredMethod(
            "methodWithExternalTaskTriggerDTO", ExternalTaskTriggerDTO.class);
    Parameter parameter = method.getParameters()[0];

    // When
    TaktParameterResolver resolver = factory.create(parameter);

    // Then
    assertThat(resolver).isInstanceOf(ExternalTaskTriggerDTOParameterResolver.class);
  }

  @Test
  void shouldCreateExternalTaskInstanceResponderResolver() throws NoSuchMethodException {
    // Given
    Method method =
        TestService.class.getDeclaredMethod(
            "methodWithExternalTaskInstanceResponder", ExternalTaskInstanceResponder.class);
    Parameter parameter = method.getParameters()[0];

    // When
    TaktParameterResolver resolver = factory.create(parameter);

    // Then
    assertThat(resolver).isInstanceOf(ExternalTaskInstanceResponderParameterResolver.class);
  }

  @Test
  void shouldCreateVariableParameterResolverWithAnnotation() throws NoSuchMethodException {
    // Given
    Method method =
        TestService.class.getDeclaredMethod("methodWithVariableAnnotation", String.class);
    Parameter parameter = method.getParameters()[0];

    // When
    TaktParameterResolver resolver = factory.create(parameter);

    // Then
    assertThat(resolver).isInstanceOf(VariableParameterResolver.class);
  }

  @Test
  void shouldCreateMapParameterResolver() throws NoSuchMethodException {
    // Given
    Method method = TestService.class.getDeclaredMethod("methodWithMap", Map.class);
    Parameter parameter = method.getParameters()[0];

    // When
    TaktParameterResolver resolver = factory.create(parameter);

    // Then
    assertThat(resolver).isInstanceOf(MapParameterResolver.class);
  }

  @Test
  void shouldCreateVariableParameterResolverByDefault() throws NoSuchMethodException {
    // Given
    Method method = TestService.class.getDeclaredMethod("methodWithCustomType", TestDto.class);
    Parameter parameter = method.getParameters()[0];

    // When
    TaktParameterResolver resolver = factory.create(parameter);

    // Then
    assertThat(resolver).isInstanceOf(VariableParameterResolver.class);
  }

  // Test service with methods that have different parameter types
  static class TestService {
    void methodWithExternalTaskTriggerDTO(ExternalTaskTriggerDTO dto) {
      // Empty Method for testing purpose
    }

    void methodWithExternalTaskInstanceResponder(ExternalTaskInstanceResponder responder) {
      // Empty Method for testing purpose
    }

    void methodWithTaktXClient(TaktXClient client) {
      // Empty Method for testing purpose
    }

    void methodWithVariableAnnotation(@Variable("customName") String value) {
      // Empty Method for testing purpose
    }

    void methodWithMap(Map<String, Object> variables) {
      // Empty Method for testing purpose
    }

    void methodWithCustomType(TestDto dto) {
      // Empty Method for testing purpose
    }
  }

  static class TestDto {
    private String value;
  }
}
