package com.flomaestro.takt.dto.v_1_0_0;

import com.fasterxml.jackson.annotation.JsonValue;

public enum TimeBucket {
  SECOND("S"),
  MINUTE("M"),
  HOURLY("H"),
  DAILY("D"),
  WEEKLY("W");

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
    if (millis < 1000) {
      return SECOND;
    } else if (millis < 60 * 1000) {
      return MINUTE;
    } else if (millis < 60 * 60 * 1000) {
      return HOURLY;
    } else if (millis < 24 * 60 * 60 * 1000) {
      return DAILY;
    } else {
      return WEEKLY;
    }
  }
}
