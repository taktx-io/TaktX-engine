/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.client;

import jakarta.annotation.Nullable;
import java.util.Map;

/** Structured local availability signal for authoritative namespace security-policy mutation. */
public record AuthoritativePolicyMutationAvailability(
    boolean observed,
    boolean available,
    @Nullable String code,
    @Nullable String message,
    Map<String, String> metadata) {

  public AuthoritativePolicyMutationAvailability {
    metadata = metadata == null || metadata.isEmpty() ? Map.of() : Map.copyOf(metadata);
  }

  public static AuthoritativePolicyMutationAvailability notObserved() {
    return new AuthoritativePolicyMutationAvailability(false, false, null, null, Map.of());
  }

  public static AuthoritativePolicyMutationAvailability availableNow() {
    return new AuthoritativePolicyMutationAvailability(true, true, null, null, Map.of());
  }

  public static AuthoritativePolicyMutationAvailability blockedNow(
      String code, String message, @Nullable Map<String, String> metadata) {
    return new AuthoritativePolicyMutationAvailability(true, false, code, message, metadata);
  }
}



