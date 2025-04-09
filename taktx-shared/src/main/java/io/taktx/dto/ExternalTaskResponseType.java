package io.taktx.dto;

import com.fasterxml.jackson.annotation.JsonValue;

public enum ExternalTaskResponseType {
  SUCCESS("S"),
  PROMISE("P"),
  TIMEOUT("T"),
  ESCALATION("ES"),
  ERROR("ER");

  private final String code;

  ExternalTaskResponseType(String code) {
    this.code = code;
  }

  @JsonValue
  public String getCode() {
    return code;
  }
}
