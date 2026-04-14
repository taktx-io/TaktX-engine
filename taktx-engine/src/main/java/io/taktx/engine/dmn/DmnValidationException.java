/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */

package io.taktx.engine.dmn;

/** Raised when DMN validation mode is STRICT and an invalid type, shape, or reference is found. */
public class DmnValidationException extends RuntimeException {

  public DmnValidationException(String message) {
    super(message);
  }

  public DmnValidationException(String message, Throwable cause) {
    super(message, cause);
  }
}
