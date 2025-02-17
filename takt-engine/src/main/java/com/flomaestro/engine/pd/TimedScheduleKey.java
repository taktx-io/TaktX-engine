package com.flomaestro.engine.pd;

import com.flomaestro.takt.dto.v_1_0_0.ScheduleKeyDTO;
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
