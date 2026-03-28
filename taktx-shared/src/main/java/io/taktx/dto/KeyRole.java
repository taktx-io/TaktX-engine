/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;

/**
 * Machine-readable role for a signing key, determining what operations the key is trusted to
 * authorize.
 *
 * <p>The role is a property of the key entry in the {@code taktx-signing-keys} topic, not of the
 * individual operation. A single process has one key with one role.
 *
 * <p>Trust hierarchy: {@code PLATFORM ⊇ ENGINE ⊇ CLIENT}.
 */
@RegisterForReflection
public enum KeyRole {
  /**
   * Engine key — trusted to authorize entry commands (StartCommandDTO, AbortTriggerDTO,
   * SetVariableTriggerDTO) via Ed25519 without JWT. Used for engine-internal operations such as
   * call-activity child starts, signal/timer-triggered starts, and child aborts.
   */
  ENGINE,
  /**
   * Client key — trusted to authorize non-entry commands (task responses, etc.) via Ed25519. Entry
   * commands from clients (startProcess, abortElementInstance, setVariable) require JWT
   * authorization regardless of the key role. This is the default role for keys published by
   * TaktXClient.
   */
  CLIENT,
  /**
   * Platform key — reserved for future platform-level trust anchoring. Implies ENGINE permissions
   * in the trust hierarchy.
   */
  PLATFORM
}
