/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks All rights reserved.
 * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 * Free use is permitted with up to 3 Kafka partitions per topic. See LICENSE file for details.
 * For commercial use or more partitions and features, contact [https://www.taktx.io/contact].
 */

package io.taktx.engine.pd;

import io.taktx.dto.ScheduleKeyDTO;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

@Getter
@AllArgsConstructor
@ToString
@EqualsAndHashCode
public class TimedScheduleKey {

  private long time;
  private ScheduleKeyDTO scheduleKey;

  public static int compareScheduleKey(TimedScheduleKey o1, TimedScheduleKey o2) {
    long diff = o1.getTime() - o2.getTime();
    if (diff != 0) {
      return diff > 0 ? 1 : -1;
    }
    long diff2 =
        o1.getScheduleKey().getTimeBucket().getPeriodMs()
            - o2.getScheduleKey().getTimeBucket().getPeriodMs();
    if (diff2 != 0) {
      return diff2 > 0 ? 1 : -1;
    }
    return o1.equals(o2) ? 0 : 1;
  }
}
