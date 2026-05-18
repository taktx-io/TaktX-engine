/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Data
@NoArgsConstructor
@EqualsAndHashCode
@ToString
@RegisterForReflection
public abstract class FlowNodeInstanceDTO {
  private ExecutionState state;

  private long elementInstanceId;

  private long parentElementInstanceId;

  private int elementIndex;

  private String elementId;

  private int passedCnt;

  private boolean incident;

  @JsonIgnore
  public boolean isActive() {
    return state == ExecutionState.ACTIVE;
  }

  @JsonIgnore
  public boolean isAborted() {
    return state == ExecutionState.ABORTED;
  }

  @JsonIgnore
  public boolean isCompleted() {
    return state == ExecutionState.COMPLETED;
  }
}
