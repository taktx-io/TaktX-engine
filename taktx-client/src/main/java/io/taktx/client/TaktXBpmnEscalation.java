/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.client;

import io.taktx.dto.VariablesDTO;

/**
 * Exception to represent a BPMN escalation event, carrying an error code, message, and associated
 * variables.
 */
public class TaktXBpmnEscalation extends RuntimeException {

  private final String errorCode;
  private final String errorMessage;
  private final VariablesDTO variables;

  /**
   * Constructor for TaktXBpmnEscalation.
   *
   * @param errorCode the BPMN escalation error code
   * @param errorMessage the BPMN escalation error message
   * @param variables the variables to be passed along with the escalation
   */
  public TaktXBpmnEscalation(String errorCode, String errorMessage, VariablesDTO variables) {
    this.errorCode = errorCode;
    this.errorMessage = errorMessage;
    this.variables = variables;
  }

  /**
   * Get the BPMN escalation error code.
   *
   * @return the error code
   */
  public String getErrorCode() {
    return errorCode;
  }

  /**
   * Get the BPMN escalation error message.
   *
   * @return the error message
   */
  public String getErrorMessage() {
    return errorMessage;
  }

  /**
   * Get the variables associated with the escalation.
   *
   * @return the variables
   */
  public VariablesDTO getVariables() {
    return variables;
  }
}
