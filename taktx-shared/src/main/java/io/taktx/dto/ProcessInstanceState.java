package io.taktx.dto;

import com.fasterxml.jackson.annotation.JsonValue;

public enum ProcessInstanceState {
  START("S"),
  ACTIVE("A"),
  COMPLETED("C"),
  TERMINATED("T"),
  FAILED("F");

  private final String code;

  ProcessInstanceState(String code) {
    this.code = code;
  }

  @JsonValue
  public String getCode() {
    return code;
  }

  public boolean isFinished() {
    return this == COMPLETED || this == TERMINATED || this == FAILED;
  }
}
