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

class TaktXBpmnErrorTest {

  @Test
  void constructor_storesAllFields() {
    VariablesDTO vars = VariablesDTO.of("key", "value");
    TaktXBpmnError error = new TaktXBpmnError(true, "ERR-001", "something went wrong", vars);

    assertThat(error.getAllowRetry()).isTrue();
    assertThat(error.getErrorCode()).isEqualTo("ERR-001");
    assertThat(error.getErrorMessage()).isEqualTo("something went wrong");
    assertThat(error.getVariables()).isSameAs(vars);
  }

  @Test
  void constructor_allowRetryFalse() {
    TaktXBpmnError error = new TaktXBpmnError(false, "ERR-002", "permanent failure", null);
    assertThat(error.getAllowRetry()).isFalse();
    assertThat(error.getVariables()).isNull();
  }

  @Test
  void isInstanceOf_runtimeException() {
    TaktXBpmnError error = new TaktXBpmnError(false, "E", "msg", null);
    assertThat(error).isInstanceOf(RuntimeException.class);
  }
}
