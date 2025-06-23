/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks All rights reserved.
 * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 * Free use is permitted with up to 3 Kafka partitions per topic. See LICENSE file for details.
 * For commercial use or more partitions and features, contact [https://www.taktx.io/contact].
 */

package io.taktx.dto;

import com.fasterxml.jackson.annotation.JsonValue;

public enum ActtivityStateEnum {
  INITIAL("I"),
  STARTED("S"),
  WAITING("W"),
  TERMINATED("T"),
  FAILED("X"),
  FINISHED("F");

  private final String code;

  ActtivityStateEnum(String code) {
    this.code = code;
  }

  @JsonValue
  public String getCode() {
    return code;
  }

  public boolean isFinished() {
    return this == FAILED || this == FINISHED || this == TERMINATED;
  }
}
