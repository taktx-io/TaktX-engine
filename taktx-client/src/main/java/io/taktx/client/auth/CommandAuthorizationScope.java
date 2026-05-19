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
  /** Authorizes starting a new process instance. */
  START_PROCESS("START"),
  /** Authorizes aborting a running process or nested element instance. */
  ABORT_PROCESS_INSTANCE("CANCEL"),
  /** Authorizes updating variables in an existing process scope. */
  SET_VARIABLE("SET_VARIABLE");
  private final String tokenAction;

  CommandAuthorizationScope(String tokenAction) {
    this.tokenAction = tokenAction;
  }

  /**
   * Returns the action string expected by the downstream token issuer.
   *
   * @return token action value to encode in the requested authorization scope
   */
  public String getTokenAction() {
    return tokenAction;
  }
}
