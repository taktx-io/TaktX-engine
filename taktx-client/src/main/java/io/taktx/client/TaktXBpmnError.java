/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.client;

import io.taktx.dto.VariablesDTO;

/** Indicates a BPMN error that can be thrown from a worker to signal a BPMN error event. */
public class TaktXBpmnError extends RuntimeException {

  private final boolean allowRetry;
  private final String errorCode;
  private final String errorMessage;
  private final VariablesDTO variables;

  /**
   * Constructor for TaktXBpmnError.
   *
   * @param allowRetry indicates whether the task can be retried
   * @param errorCode the BPMN error code
   * @param errorMessage the BPMN error message
   * @param variables the variables to be passed along with the error
   */
  public TaktXBpmnError(
      boolean allowRetry, String errorCode, String errorMessage, VariablesDTO variables) {
    this.allowRetry = allowRetry;
    this.errorCode = errorCode;
    this.errorMessage = errorMessage;
    this.variables = variables;
  }

  /**
   * Indicates whether the task can be retried.
   *
   * @return true if the task can be retried, false otherwise
   */
  public boolean getAllowRetry() {
    return allowRetry;
  }

  /**
   * Get the BPMN error code.
   *
   * @return the error code
   */
  public String getErrorCode() {
    return errorCode;
  }

  /**
   * Get the BPMN error message.
   *
   * @return the error message
   */
  public String getErrorMessage() {
    return errorMessage;
  }

  /**
   * Get the variables associated with the error.
   *
   * @return the variables
   */
  public VariablesDTO getVariables() {
    return variables;
  }
}
