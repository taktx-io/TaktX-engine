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
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeInfo.Id;
import com.fasterxml.jackson.databind.annotation.JsonTypeIdResolver;
import io.taktx.MessageSchedulerTypeIdResolver;
import lombok.Getter;
import lombok.NoArgsConstructor;

@JsonTypeInfo(use = Id.CUSTOM, property = "c")
@JsonFormat(shape = Shape.ARRAY)
@JsonTypeIdResolver(MessageSchedulerTypeIdResolver.class)
@NoArgsConstructor
@Getter
public abstract class MessageScheduleDTO {

  protected SchedulableMessageDTO message;

  protected long instantiationTime;

  protected MessageScheduleDTO(SchedulableMessageDTO message, long instantiationTime) {
    this.message = message;
    this.instantiationTime = instantiationTime;
  }

  @JsonIgnore
  public abstract Long getNextExecutionTime(long timestamp);
}
