/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks All rights reserved.
 * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 * Free use is permitted with up to 3 Kafka partitions per topic. See LICENSE file for details.
 * For commercial use or more partitions and features, contact [https://www.taktx.io/contact].
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
public abstract class FlowNodeInstanceDTO {
  private long elementInstanceId;

  private long parentElementInstanceId;

  private int elementIndex;

  private String elementId;

  private int passedCnt;

  @JsonIgnore
  public boolean isTerminated() {
    return false;
  }

  @JsonIgnore
  public boolean isFailed() {
    return false;
  }

  @JsonIgnore
  public boolean isWaiting() {
    return false;
  }
}
