package com.flomaestro.client;

import lombok.Getter;

@Getter
public class ErrorEventException extends RuntimeException {

  private final boolean allowRetry;
  private final String name;
  private final String code;

  public ErrorEventException(boolean allowRetry, String name, String code, String message) {
    super(message);
    this.allowRetry = allowRetry;
    this.name = name;
    this.code = code;
  }
}
