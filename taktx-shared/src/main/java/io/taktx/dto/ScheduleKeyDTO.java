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
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeInfo.Id;
import com.fasterxml.jackson.databind.annotation.JsonTypeIdResolver;
import io.taktx.ScheduleKeyTypeIdResolver;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@JsonTypeInfo(use = Id.CUSTOM, property = "c")
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonTypeIdResolver(ScheduleKeyTypeIdResolver.class)
@JsonFormat(shape = Shape.ARRAY)
@EqualsAndHashCode
@Getter
@Setter
@NoArgsConstructor
@ToString
public abstract class ScheduleKeyDTO {

  private TimeBucket timeBucket;

  protected ScheduleKeyDTO(TimeBucket timeBucket) {
    this.timeBucket = timeBucket;
  }
}
