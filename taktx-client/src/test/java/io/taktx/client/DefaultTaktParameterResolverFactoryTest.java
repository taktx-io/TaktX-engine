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

import io.taktx.client.annotation.Variable;
import io.taktx.dto.ExternalTaskTriggerDTO;
import io.taktx.dto.VariablesDTO;
import io.taktx.variables.Variables;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DefaultTaktParameterResolverFactoryTest {

  private DefaultParameterResolverFactory factory;

  @BeforeEach
  void setUp() {
    ProcessInstanceResponder externalTaskResponder = mock(ProcessInstanceResponder.class);
    factory = new DefaultParameterResolverFactory(externalTaskResponder);
  }

  @Test
  void shouldCreateExternalTaskTriggerDTOResolver() throws NoSuchMethodException {
    // Given
    Method method =
        TestService.class.getDeclaredMethod(
            "methodWithExternalTaskTriggerDTO", ExternalTaskTriggerDTO.class);
    Parameter parameter = method.getParameters()[0];

    // When
    ParameterResolver resolver = factory.create(parameter);

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
    ParameterResolver resolver = factory.create(parameter);

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
    ParameterResolver resolver = factory.create(parameter);

    // Then
    assertThat(resolver).isInstanceOf(VariableParameterResolver.class);
  }

  @Test
  void shouldCreateMapParameterResolver() throws NoSuchMethodException {
    // Given
    Method method = TestService.class.getDeclaredMethod("methodWithMap", Map.class);
    Parameter parameter = method.getParameters()[0];

    // When
    ParameterResolver resolver = factory.create(parameter);

    // Then
    assertThat(resolver).isInstanceOf(MapParameterResolver.class);
  }

  @Test
  void shouldCreateVariableParameterResolverByDefault() throws NoSuchMethodException {
    // Given
    Method method = TestService.class.getDeclaredMethod("methodWithCustomType", TestDto.class);
    Parameter parameter = method.getParameters()[0];

    // When
    ParameterResolver resolver = factory.create(parameter);

    // Then
    assertThat(resolver).isInstanceOf(VariablesObjectParameterResolver.class);
  }

  @Test
  void shouldCreateVariableParameterResolverForListParameter() throws NoSuchMethodException {
    Method method = TestService.class.getDeclaredMethod("methodWithList", List.class);
    Parameter parameter = method.getParameters()[0];

    ParameterResolver resolver = factory.create(parameter);

    assertThat(resolver).isInstanceOf(VariableParameterResolver.class);
  }

  @Test
  void shouldResolveListParameterFromVariableWithMatchingName() throws NoSuchMethodException {
    Method method = TestService.class.getDeclaredMethod("methodWithList", List.class);
    Parameter parameter = method.getParameters()[0];
    ParameterResolver resolver = factory.create(parameter);
    ExternalTaskTriggerDTO trigger =
        ExternalTaskTriggerDTO.builder()
            .variables(
                VariablesDTO.ofVariableMap(
                    Variables.map(parameter.getName(), List.of("invoice-1", "invoice-2"))))
            .build();

    Object resolved = resolver.resolve(trigger);

    assertThat(resolved).isEqualTo(List.of("invoice-1", "invoice-2"));
  }

  // Test service with methods that have different parameter types
  @SuppressWarnings("unused")
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

    void methodWithList(List<String> invoiceIds) {
      // Empty Method for testing purpose
    }
  }

  @SuppressWarnings("unused")
  static class TestDto {
    private String value;
  }
}
