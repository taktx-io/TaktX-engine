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
public enum FlowNodeStateEnum {
  INITIAL("I"), // Node instance is created, but not yet started.
  ACTIVE("A"), // Node is “doing its job”:
  CANCELED(
      "C"), // Node was terminated early by an interrupting boundary event or by its parent scope
  // being canceled (e.g. terminate end event in parent).
  ABORTED("X"), // Node threw an Error event that wasn’t caught locally.
  COMPLETED("F"); // Node finished normally.

  private final String code;

  FlowNodeStateEnum(String code) {
    this.code = code;
  }

  @JsonValue
  public String getCode() {
    return code;
  }

  @JsonIgnore
  public boolean isFinished() {
    return this == ABORTED || this == COMPLETED || this == CANCELED;
  }
}
