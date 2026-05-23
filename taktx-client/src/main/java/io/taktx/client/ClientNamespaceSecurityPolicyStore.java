/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.client;

import io.taktx.dto.NamespaceSecurityPolicyDTO;
import io.taktx.dto.SecurityActivationState;
import io.taktx.security.NamespaceSecurityPolicySupport;
import jakarta.annotation.Nullable;
import java.util.concurrent.atomic.AtomicReference;

/** In-memory view of the namespace security policy authoritative for client-side protected traffic. */
final class ClientNamespaceSecurityPolicyStore {

  private final AtomicReference<NamespaceSecurityPolicyDTO> currentPolicy = new AtomicReference<>(null);
  private final AtomicReference<NamespaceSecurityPolicyDTO> activePolicy = new AtomicReference<>(null);

  synchronized void update(@Nullable NamespaceSecurityPolicyDTO dto) {
    NamespaceSecurityPolicyDTO validated =
        dto == null ? null : NamespaceSecurityPolicySupport.requireValid(dto);
    currentPolicy.set(validated);
    if (validated != null && validated.getActivationState() == SecurityActivationState.ACTIVE) {
      activePolicy.set(validated);
    }
  }

  synchronized void clear() {
    currentPolicy.set(null);
    activePolicy.set(null);
  }

  synchronized @Nullable NamespaceSecurityPolicyDTO get() {
    return currentPolicy.get();
  }

  synchronized @Nullable NamespaceSecurityPolicyDTO getAuthoritativePolicy() {
    NamespaceSecurityPolicyDTO current = currentPolicy.get();
    if (current != null && current.getActivationState() == SecurityActivationState.ACTIVE) {
      return current;
    }
    return activePolicy.get();
  }

  synchronized void setCurrentPolicy(@Nullable NamespaceSecurityPolicyDTO dto) {
    currentPolicy.set(dto == null ? null : NamespaceSecurityPolicySupport.requireValid(dto));
  }

  synchronized void setActivePolicy(@Nullable NamespaceSecurityPolicyDTO dto) {
    activePolicy.set(dto == null ? null : NamespaceSecurityPolicySupport.requireValid(dto));
  }
}

