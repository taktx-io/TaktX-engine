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
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeInfo.Id;
import com.fasterxml.jackson.databind.annotation.JsonTypeIdResolver;
import io.quarkus.runtime.annotations.RegisterForReflection;
import io.taktx.MessageSchedulerTypeIdResolver;
import lombok.Getter;
import lombok.NoArgsConstructor;

@JsonTypeInfo(use = Id.CUSTOM, property = "c")
@JsonFormat(shape = Shape.ARRAY)
@JsonTypeIdResolver(MessageSchedulerTypeIdResolver.class)
@NoArgsConstructor
@Getter
@RegisterForReflection
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
