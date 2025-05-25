package io.taktx.dto;

import com.fasterxml.jackson.annotation.JsonValue;

public enum UserTaskResponseType {
  COMPLETED("C"),
  ESCALATION("ES"),
  ERROR("ER");

  private final String code;

  UserTaskResponseType(String code) {
    this.code = code;
  }

  @JsonValue
  public String getCode() {
    return code;
  }
}
