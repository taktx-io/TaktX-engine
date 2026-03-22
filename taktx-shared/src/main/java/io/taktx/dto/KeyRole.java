/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks All rights reserved.
 * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 * Free use is permitted with up to 3 Kafka partitions per topic. See LICENSE file for details.
 * For commercial use or more partitions and features, contact [https://www.taktx.io/contact].
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
   * Engine key — trusted to authorize entry commands (StartCommandDTO, AbortTriggerDTO) via Ed25519
   * without JWT. Used for engine-internal operations such as call-activity child starts,
   * signal/timer-triggered starts, and child aborts.
   */
  ENGINE,
  /**
   * Client key — trusted to authorize non-entry commands (task responses, set-variable) via
   * Ed25519. Entry commands from clients require JWT authorization regardless of the key role. This
   * is the default role for keys published by TaktXClient.
   */
  CLIENT,
  /**
   * Platform key — reserved for future platform-level trust anchoring. Implies ENGINE permissions
   * in the trust hierarchy.
   */
  PLATFORM
}
