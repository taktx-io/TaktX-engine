/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.security;

/** Policy elements that may be relevant to a participant role for readiness/gating decisions. */
public enum RoleRelevantPolicyElement {
  MODE,
  TRUST_ANCHOR_REQUIRED,
  REQUIRED_SIGNING_ENGINE_OUTBOUND,
  REQUIRED_SIGNING_CLIENT_COMMANDS,
  REQUIRED_SIGNING_WORKER_RESPONSES,
  REQUIRED_AUTHORIZATION_START_COMMANDS,
  REQUIRED_AUTHORIZATION_EXTERNAL_TASK_COMPLETION,
  REQUIRED_AUTHORIZATION_USER_TASK_COMPLETION
}

