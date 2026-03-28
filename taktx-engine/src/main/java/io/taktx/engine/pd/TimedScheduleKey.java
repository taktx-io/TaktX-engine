/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
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
