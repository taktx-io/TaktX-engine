/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.client;

import static org.assertj.core.api.Assertions.assertThat;

import io.taktx.dto.ExternalTaskTriggerDTO;
import io.taktx.dto.VariablesDTO;
import io.taktx.proto.VariableValue;
import io.taktx.variables.Variables;
import org.junit.jupiter.api.Test;

class VariableParameterResolverTest {

  @Test
  void resolvesRawVariableValueFromInboundTrigger() {
    VariableParameterResolver resolver =
        new VariableParameterResolver(VariableValue.class, "amount");
    ExternalTaskTriggerDTO trigger =
        ExternalTaskTriggerDTO.builder()
            .variables(VariablesDTO.ofVariableMap(Variables.map("amount", 42L)))
            .build();

    Object resolved = resolver.resolve(trigger);

    assertThat(resolved).isEqualTo(Variables.of(42L));
  }

  @Test
  void resolvesPlainJavaTypeFromInboundTrigger() {
    VariableParameterResolver resolver = new VariableParameterResolver(String.class, "name");
    ExternalTaskTriggerDTO trigger =
        ExternalTaskTriggerDTO.builder()
            .variables(VariablesDTO.ofVariableMap(Variables.map("name", "test")))
            .build();

    assertThat(resolver.resolve(trigger)).isEqualTo("test");
  }
}
