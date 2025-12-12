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
public enum ExecutionState {
  // For scope: Scope is created but no child node has started yet.
  // For flow node: Node instance is created, but not yet started.
  INITIALIZED("S"),
  // For scope: At least one child execution (flow node instance) is active.
  // For flow node: Node is “doing its job”:
  ACTIVE("A"),
  // For scope:  All required child executions finished normally.
  // For flow node: Node finished normally.
  COMPLETED("C"),
  // For scope: A child threw an uncaught Error.
  // For flow node: A child threw an uncaught Error.
  ABORTED("F");

  private final String code;

  ExecutionState(String code) {
    this.code = code;
  }

  @JsonValue
  public String getCode() {
    return code;
  }

  @JsonIgnore
  public boolean isDone() {
    return this == COMPLETED || this == ABORTED;
  }
}
