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
import java.time.Instant;
import java.util.List;
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

  @Test
  void resolvesCustomObjectFromSingleVariable() {
    VariableParameterResolver resolver = new VariableParameterResolver(OrderContext.class, "order");
    ExternalTaskTriggerDTO trigger =
        ExternalTaskTriggerDTO.builder()
            .variables(
                VariablesDTO.ofVariableMap(
                    Variables.map(
                        "order", new OrderContext("INV-1", Instant.parse("2026-05-19T10:15:30Z")))))
            .build();

    Object resolved = resolver.resolve(trigger);

    assertThat(resolved)
        .isEqualTo(new OrderContext("INV-1", Instant.parse("2026-05-19T10:15:30Z")));
  }

  @Test
  void resolvesFullVariableScopeIntoCustomObject() {
    VariablesObjectParameterResolver resolver =
        new VariablesObjectParameterResolver(OrderWorkerInput.class);
    ExternalTaskTriggerDTO trigger =
        ExternalTaskTriggerDTO.builder()
            .variables(
                VariablesDTO.ofVariableMap(
                    Variables.map(
                        "invoiceId",
                        "INV-1",
                        "createdAt",
                        Instant.parse("2026-05-19T10:15:30Z"),
                        "approved",
                        true)))
            .build();

    Object resolved = resolver.resolve(trigger);

    assertThat(resolved)
        .isEqualTo(new OrderWorkerInput("INV-1", Instant.parse("2026-05-19T10:15:30Z"), true));
  }

  @Test
  void resolvesListVariableFromInboundTrigger() {
    VariableParameterResolver resolver = new VariableParameterResolver(List.class, "invoiceIds");
    ExternalTaskTriggerDTO trigger =
        ExternalTaskTriggerDTO.builder()
            .variables(
                VariablesDTO.ofVariableMap(
                    Variables.map("invoiceIds", List.of("INV-1", "INV-2", "INV-3"))))
            .build();

    Object resolved = resolver.resolve(trigger);

    assertThat(resolved).isEqualTo(List.of("INV-1", "INV-2", "INV-3"));
  }

  record OrderContext(String invoiceId, Instant createdAt) {}

  record OrderWorkerInput(String invoiceId, Instant createdAt, boolean approved) {}
}
