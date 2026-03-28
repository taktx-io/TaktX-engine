/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Getter
@ToString
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@RegisterForReflection
public class FixedRateMessageScheduleDTO extends MessageScheduleDTO {

  private long period;

  private int repetitions;

  public FixedRateMessageScheduleDTO(
      SchedulableMessageDTO message, long period, int repetitions, long instantiation) {
    super(message, instantiation);
    this.period = period;
    this.repetitions = repetitions;
  }

  @Override
  public Long getNextExecutionTime(long from) {
    if (period <= 0 || from < instantiationTime) {
      return null;
    }

    long nrOfFits = (from - instantiationTime) / period;
    if (repetitions < 2) {
      return instantiationTime + (nrOfFits + 1) * period;
    } else {
      if (nrOfFits < repetitions) {
        return instantiationTime + (nrOfFits + 1) * period;
      } else {
        return null;
      }
    }
  }
}
