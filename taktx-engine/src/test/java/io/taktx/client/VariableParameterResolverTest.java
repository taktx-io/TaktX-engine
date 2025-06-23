/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks All rights reserved.
 * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 * Free use is permitted with up to 3 Kafka partitions per topic. See LICENSE file for details.
 * For commercial use or more partitions and features, contact [https://www.taktx.io/contact].
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
