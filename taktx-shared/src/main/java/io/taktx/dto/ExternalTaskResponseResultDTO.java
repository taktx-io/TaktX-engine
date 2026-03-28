/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonFormat.Shape;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Getter
@NoArgsConstructor
@EqualsAndHashCode
@ToString
@JsonFormat(shape = Shape.ARRAY)
@JsonInclude(JsonInclude.Include.NON_NULL)
@RegisterForReflection
public class ExternalTaskResponseResultDTO {
  private ExternalTaskResponseType responseType;

  private String code;

  private String message;

  private Boolean allowRetry;

  private long timeout;

  private String[] stacktrace;

  public ExternalTaskResponseResultDTO(
      ExternalTaskResponseType responseType,
      Boolean allowRetry,
      String code,
      String message,
      long timeout) {
    this(responseType, allowRetry, code, message, timeout, null);
  }

  public ExternalTaskResponseResultDTO(
      ExternalTaskResponseType responseType,
      Boolean allowRetry,
      String code,
      String message,
      long timeout,
      String[] stacktrace) {
    this.responseType = responseType;
    this.code = code;
    this.message = message;
    this.allowRetry = allowRetry;
    this.timeout = timeout;
    this.stacktrace = stacktrace;
  }
}
