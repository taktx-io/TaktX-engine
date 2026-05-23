/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.engine.config;

import io.taktx.dto.NamespaceSecurityPolicyDTO;
import io.taktx.security.NamespaceSecurityPolicySupport;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.concurrent.atomic.AtomicReference;

/**
 * CDI bean that holds the latest {@link NamespaceSecurityPolicyDTO} received from the
 * namespace-local {@code taktx-security-policy} compacted topic.
 *
 * <p>Written by the namespace security policy global-store processor on the Kafka Streams
 * GlobalStreamThread and read by runtime security components as the explicit namespace-policy
 * contract is adopted.
 */
@ApplicationScoped
public class NamespaceSecurityPolicyStore {

  private final AtomicReference<NamespaceSecurityPolicyDTO> currentPolicy = new AtomicReference<>(null);
  private final AtomicReference<NamespaceSecurityPolicyDTO> activePolicy = new AtomicReference<>(null);
  private final AtomicReference<Long> validationStartedAtMs = new AtomicReference<>(null);

  /** Called whenever a policy record is received and validated successfully. */
  public synchronized void update(NamespaceSecurityPolicyDTO dto) {
    NamespaceSecurityPolicyDTO validated =
        dto == null ? null : NamespaceSecurityPolicySupport.requireValid(dto);
    currentPolicy.set(validated);
    if (validated != null
        && validated.getActivationState() == io.taktx.dto.SecurityActivationState.ACTIVE) {
      activePolicy.set(validated);
      validationStartedAtMs.set(null);
    }
  }

  /** Clears the latest explicit policy so callers fall back to legacy/default semantics. */
  public synchronized void clear() {
    currentPolicy.set(null);
    activePolicy.set(null);
    validationStartedAtMs.set(null);
  }

  /** Returns the latest validated policy record, or {@code null} if no explicit policy exists. */
  public synchronized NamespaceSecurityPolicyDTO get() {
    return currentPolicy.get();
  }

  /**
   * Returns the policy currently authoritative for protected data-plane behavior.
   *
   * <p>If a newer policy is still `REQUESTED` / `VALIDATING`, the previous `ACTIVE` policy remains
   * authoritative until activation succeeds.
   */
  public synchronized NamespaceSecurityPolicyDTO getAuthoritativePolicy() {
    NamespaceSecurityPolicyDTO current = currentPolicy.get();
    if (current != null
        && current.getActivationState() == io.taktx.dto.SecurityActivationState.ACTIVE) {
      return current;
    }
    return activePolicy.get();
  }

  /** Replaces only the current policy view used by runtime readers. */
  public synchronized void setCurrentPolicy(NamespaceSecurityPolicyDTO dto) {
    currentPolicy.set(dto == null ? null : NamespaceSecurityPolicySupport.requireValid(dto));
  }

  /** Returns the last authoritative ACTIVE policy, if any. */
  public synchronized NamespaceSecurityPolicyDTO getActivePolicy() {
    return activePolicy.get();
  }

  /** Persists the last authoritative ACTIVE policy used for rollback / protected data-plane rules. */
  public synchronized void setActivePolicy(NamespaceSecurityPolicyDTO dto) {
    activePolicy.set(dto == null ? null : NamespaceSecurityPolicySupport.requireValid(dto));
  }

  /** Clears only the current policy view while preserving the last ACTIVE policy for rollback. */
  public synchronized void clearCurrentPolicy() {
    currentPolicy.set(null);
    validationStartedAtMs.set(null);
  }

  public synchronized Long getValidationStartedAtMs() {
    return validationStartedAtMs.get();
  }

  public synchronized void setValidationStartedAtMs(Long startedAtMs) {
    validationStartedAtMs.set(startedAtMs);
  }
}
