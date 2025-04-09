package io.taktx.dto;

import com.fasterxml.jackson.annotation.JsonValue;

public enum CatchEventStateEnum {
  INITIAL("I"),
  STARTED("R"),
  WAITING("W"),
  FINISHED("F"),
  TERMINATED("T");

  private final String code;

  CatchEventStateEnum(String code) {
    this.code = code;
  }

  @JsonValue
  public String getCode() {
    return code;
  }
}
