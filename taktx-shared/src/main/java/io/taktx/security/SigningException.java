/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.security;

/** Runtime exception thrown when a cryptographic signing or key operation fails. */
public class SigningException extends RuntimeException {

  public SigningException(String message) {
    super(message);
  }

  public SigningException(String message, Throwable cause) {
    super(message, cause);
  }
}
