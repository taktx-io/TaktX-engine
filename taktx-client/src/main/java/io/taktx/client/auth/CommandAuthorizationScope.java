/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.client.auth;

/** Identifies the outbound command scope for which a JWT is requested. */
public enum CommandAuthorizationScope {
  START_PROCESS("START"),
  ABORT_PROCESS_INSTANCE("CANCEL");
  private final String tokenAction;

  CommandAuthorizationScope(String tokenAction) {
    this.tokenAction = tokenAction;
  }

  public String getTokenAction() {
    return tokenAction;
  }
}
