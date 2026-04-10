/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.client;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class InstanceUpdateStartStrategyTest {

  @Test
  void values_containsResumeAndEarliest() {
    assertThat(InstanceUpdateStartStrategy.values())
        .containsExactlyInAnyOrder(
            InstanceUpdateStartStrategy.RESUME, InstanceUpdateStartStrategy.EARLIEST);
  }

  @Test
  void valueOf_resume() {
    assertThat(InstanceUpdateStartStrategy.valueOf("RESUME"))
        .isEqualTo(InstanceUpdateStartStrategy.RESUME);
  }

  @Test
  void valueOf_earliest() {
    assertThat(InstanceUpdateStartStrategy.valueOf("EARLIEST"))
        .isEqualTo(InstanceUpdateStartStrategy.EARLIEST);
  }
}
