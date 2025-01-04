package com.flomaestro.takt.dto.v_1_0_0;

import com.fasterxml.jackson.annotation.JsonValue;

public enum CatchEventStateEnum {
  READY("R"),
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
