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
