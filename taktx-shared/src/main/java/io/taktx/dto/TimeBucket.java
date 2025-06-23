/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks All rights reserved.
 * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 * Free use is permitted with up to 3 Kafka partitions per topic. See LICENSE file for details.
 * For commercial use or more partitions and features, contact [https://www.taktx.io/contact].
 */

package io.taktx.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonValue;

public enum TimeBucket {
  MINUTE("M", Constants.MINUTE_MS, "minute"),
  HOURLY("H", Constants.HOUR_MS, "hourly"),
  DAILY("D", Constants.DAY_MS, "daily"),
  WEEKLY("W", Constants.WEEK_MS, "weekly"),
  YEARLY("L", Constants.YEAR_MS, "yearly");

  private final String value;
  private final long periodMs;
  private final String name;

  TimeBucket(String value, long periodMs, String name) {
    this.value = value;
    this.periodMs = periodMs;
    this.name = name;
  }

  @JsonIgnore
  public String getName() {
    return name;
  }

  @JsonIgnore
  public long getPeriodMs() {
    return periodMs;
  }

  @JsonValue
  public String getValue() {
    return value;
  }

  public static TimeBucket ofMillis(long millis) {
    if (millis < Constants.MINUTE_MS) {
      return MINUTE;
    } else if (millis < Constants.HOUR_MS) {
      return HOURLY;
    } else if (millis < Constants.DAY_MS) {
      return DAILY;
    } else if (millis < Constants.WEEK_MS) {
      return DAILY;
    } else {
      return YEARLY;
    }
  }

  public static class Constants {
    private Constants() {
      // Prevent instantiations
    }

    public static final long MINUTE_MS = 60000;
    public static final long HOUR_MS = MINUTE_MS * 60;
    public static final long DAY_MS = HOUR_MS * 24;
    public static final long WEEK_MS = DAY_MS * 7;
    public static final long YEAR_MS = WEEK_MS * 52;
  }
}
