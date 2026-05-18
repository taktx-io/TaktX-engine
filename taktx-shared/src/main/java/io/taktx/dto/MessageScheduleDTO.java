/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.Getter;
import lombok.NoArgsConstructor;

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

  public abstract Long getNextExecutionTime(long timestamp);
}
