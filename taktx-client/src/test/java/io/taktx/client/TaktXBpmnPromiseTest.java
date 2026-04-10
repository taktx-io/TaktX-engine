/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.client;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class TaktXBpmnPromiseTest {

  @Test
  void constructor_storesDuration() {
    Duration d = Duration.ofSeconds(30);
    TaktXBpmnPromise promise = new TaktXBpmnPromise(d);
    assertThat(promise.getDuration()).isEqualTo(d);
  }

  @Test
  void isInstanceOf_runtimeException() {
    assertThat(new TaktXBpmnPromise(Duration.ZERO)).isInstanceOf(RuntimeException.class);
  }
}
