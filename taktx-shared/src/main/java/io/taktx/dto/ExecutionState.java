/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
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
