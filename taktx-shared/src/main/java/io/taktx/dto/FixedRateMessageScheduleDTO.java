/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks All rights reserved.
 * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 * Free use is permitted with up to 3 Kafka partitions per topic. See LICENSE file for details.
 * For commercial use or more partitions and features, contact [https://www.taktx.io/contact].
 */

package io.taktx.dto;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Getter
@ToString
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
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
