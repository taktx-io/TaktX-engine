/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.client;

import static org.assertj.core.api.Assertions.assertThat;

import io.taktx.dto.VariablesDTO;
import org.junit.jupiter.api.Test;

class TaktXBpmnEscalationTest {

  @Test
  void constructor_storesAllFields() {
    VariablesDTO vars = VariablesDTO.of("k", "v");
    TaktXBpmnEscalation esc = new TaktXBpmnEscalation("ESC-01", "escalation message", vars);

    assertThat(esc.getErrorCode()).isEqualTo("ESC-01");
    assertThat(esc.getErrorMessage()).isEqualTo("escalation message");
    assertThat(esc.getVariables()).isSameAs(vars);
  }

  @Test
  void constructor_withNullVariables() {
    TaktXBpmnEscalation esc = new TaktXBpmnEscalation("ESC-02", "msg", null);
    assertThat(esc.getVariables()).isNull();
  }

  @Test
  void isInstanceOf_runtimeException() {
    TaktXBpmnEscalation esc = new TaktXBpmnEscalation("E", "m", null);
    assertThat(esc).isInstanceOf(RuntimeException.class);
  }
}
