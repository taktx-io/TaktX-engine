/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.client;

import jakarta.annotation.Nullable;

/**
 * Hook for fail-closed protected data-plane participation checks in client publishers/consumers.
 */
@FunctionalInterface
interface ProtectedClientDataPlaneGuard {

  void check(
      ProtectedClientDataPlaneOperation operation, @Nullable String explicitAuthorizationToken);

  static ProtectedClientDataPlaneGuard noop() {
    return (operation, explicitAuthorizationToken) -> {};
  }
}
