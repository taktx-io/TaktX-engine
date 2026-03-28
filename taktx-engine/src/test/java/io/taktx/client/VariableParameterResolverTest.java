/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */

package io.taktx.client;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.taktx.dto.ExternalTaskTriggerDTO;
import io.taktx.dto.VariablesDTO;
import org.junit.jupiter.api.Test;

class VariableParameterResolverTest {

  @Test
  void test() {
    VariableParameterResolver resolver =
        new VariableParameterResolver(new ObjectMapper(), String.class, "name");
    VariablesDTO vars = VariablesDTO.of("name", "test");
    ExternalTaskTriggerDTO trigger = ExternalTaskTriggerDTO.builder().variables(vars).build();
    Object resolve = resolver.resolve(trigger);
    assertThat(resolve).isEqualTo("test");
  }
}
