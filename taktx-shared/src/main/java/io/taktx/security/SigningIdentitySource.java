/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.security;

/**
 * Supplies the currently active signing identity.
 *
 * <p>Implementations may be static, externally managed (for example mounted-file based), or
 * application-managed local persistence sources.
 */
public interface SigningIdentitySource {

  /** Returns the currently active identity, or {@code null} when signing is unavailable. */
  SigningIdentity currentIdentity();

  /**
   * Returns whether this source is expected to preserve the same identity across normal restarts.
   *
   * <p>The default is {@code true} so custom stable sources are not penalized. Ephemeral generated
   * sources should override this to return {@code false}.
   */
  default boolean isRestartStable() {
    return true;
  }

  /**
   * Returns whether this source is expected to rotate identities while the process remains running.
   *
   * <p>The default is {@code false}. Sources that deliberately watch external key material and
   * update in place should override this to return {@code true}.
   */
  default boolean supportsLiveRotation() {
    return false;
  }

  default String getSourceType() {
    return getClass().getSimpleName();
  }
}
