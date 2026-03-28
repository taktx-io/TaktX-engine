/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonFormat.Shape;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeInfo.Id;
import com.fasterxml.jackson.databind.annotation.JsonTypeIdResolver;
import io.quarkus.runtime.annotations.RegisterForReflection;
import io.taktx.FlowNodeInstanceTypeIdResolver;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

@JsonTypeInfo(use = Id.CUSTOM, property = "c")
@JsonFormat(shape = Shape.ARRAY)
@JsonTypeIdResolver(FlowNodeInstanceTypeIdResolver.class)
@JsonInclude(Include.NON_NULL)
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
