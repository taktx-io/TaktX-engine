/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks All rights reserved.
 * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 * Free use is permitted with up to 3 Kafka partitions per topic. See LICENSE file for details.
 * For commercial use or more partitions and features, contact [https://www.taktx.io/contact].
 */

package io.taktx.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonValue;
import io.quarkus.runtime.annotations.RegisterForReflection;

@RegisterForReflection
public enum ScopeState {
  INITIALIZED("S"), // Scope is created but no child node has started yet.
  ACTIVE("A"), // At least one child execution (flow node instance) is active.
  COMPLETED("C"), // All required child executions finished normally.
  CANCELED("T"), // Scope terminated by parent scope or by an interrupting boundary event.
  ABORTED("F"); // A child threw an uncaught Error.

  private final String code;

  ScopeState(String code) {
    this.code = code;
  }

  @JsonValue
  public String getCode() {
    return code;
  }

  @JsonIgnore
  public boolean isDone() {
    return this == COMPLETED || this == CANCELED || this == ABORTED;
  }
}
