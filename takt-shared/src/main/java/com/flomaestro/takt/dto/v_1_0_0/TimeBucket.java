package com.flomaestro.takt.dto.v_1_0_0;

import com.fasterxml.jackson.annotation.JsonValue;

public enum TimeBucket {
  SECOND("S"),
  MINUTE("M"),
  HOURLY("H"),
  DAILY("D"),
  WEEKLY("W");

  private static final int SECOND_MS = 1000;
  private static final int MINUTE_MS = 60 * 1000;
  private static final int HOUR_MS = 60 * 60 * 1000;
  private static final int DAY_MS = 24 * 60 * 60 * 1000;

  private final String value;

  TimeBucket(String value) {
    this.value = value;
  }

  @JsonValue
  public String getValue() {
    return value;
  }

  public static TimeBucket ofMillis(long millis) {
    if (millis < 0) {
      throw new IllegalArgumentException("millis must be greater than or equal to 0");
    }
    if (millis <= SECOND_MS) {
      return SECOND;
    } else if (millis <= MINUTE_MS) {
      return MINUTE;
    } else if (millis <= HOUR_MS) {
      return HOURLY;
    } else if (millis <= DAY_MS) {
      return DAILY;
    } else {
      return WEEKLY;
    }
  }
}
