/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks All rights reserved.
 * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 * Free use is permitted with up to 3 Kafka partitions per topic. See LICENSE file for details.
 * For commercial use or more partitions and features, contact [https://www.taktx.io/contact].
 */
package io.taktx.security;

/** Supplies the currently active signing identity. Implementations may change over time. */
public interface SigningIdentitySource {

  /** Returns the currently active identity, or {@code null} when signing is unavailable. */
  SigningIdentity currentIdentity();

  default String getSourceType() {
    return getClass().getSimpleName();
  }
}
