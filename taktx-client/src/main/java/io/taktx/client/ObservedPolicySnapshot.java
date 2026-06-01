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
import io.taktx.dto.SecurityMode;
import jakarta.annotation.Nullable;

/**
 * Public snapshot of the namespace security policy posture observed by the client via control-plane
 * topics only.
 */
public record ObservedPolicySnapshot(
    @Nullable NamespaceSecurityPolicyDTO currentPolicy,
    @Nullable NamespaceSecurityPolicyDTO authoritativePolicy) {

  public static ObservedPolicySnapshot empty() {
    return new ObservedPolicySnapshot(null, null);
  }

  public boolean hasCurrentPolicy() {
    return currentPolicy != null;
  }

  public boolean hasAuthoritativePolicy() {
    return authoritativePolicy != null;
  }

  public @Nullable SecurityActivationState currentActivationState() {
    return null;
  }

  public @Nullable NamespaceSecurityPolicyDTO requestedPolicy() {
    return currentPolicy != null ? currentPolicy : activePolicy();
  }

  public @Nullable NamespaceSecurityPolicyDTO activePolicy() {
    return authoritativePolicy != null ? authoritativePolicy : currentPolicy;
  }

  public @Nullable SecurityMode requestedMode() {
    NamespaceSecurityPolicyDTO requestedPolicy = requestedPolicy();
    return requestedPolicy != null ? requestedPolicy.getMode() : null;
  }

  public @Nullable Long requestedPolicyVersion() {
    NamespaceSecurityPolicyDTO requestedPolicy = requestedPolicy();
    return requestedPolicy != null ? requestedPolicy.getPolicyVersion() : null;
  }

  public @Nullable String requestedPolicyHash() {
    NamespaceSecurityPolicyDTO requestedPolicy = requestedPolicy();
    return requestedPolicy != null ? requestedPolicy.getPolicyHash() : null;
  }

  public @Nullable SecurityMode activeMode() {
    NamespaceSecurityPolicyDTO activePolicy = activePolicy();
    return activePolicy != null ? activePolicy.getMode() : null;
  }

  public @Nullable Long activePolicyVersion() {
    NamespaceSecurityPolicyDTO activePolicy = activePolicy();
    return activePolicy != null ? activePolicy.getPolicyVersion() : null;
  }

  public @Nullable String activePolicyHash() {
    NamespaceSecurityPolicyDTO activePolicy = activePolicy();
    return activePolicy != null ? activePolicy.getPolicyHash() : null;
  }

  public @Nullable NamespaceSecurityPolicyDTO effectivePolicy() {
    return authoritativePolicy != null ? authoritativePolicy : currentPolicy;
  }

  public @Nullable SecurityMode effectiveMode() {
    NamespaceSecurityPolicyDTO effectivePolicy = effectivePolicy();
    return effectivePolicy != null ? effectivePolicy.getMode() : null;
  }

  public @Nullable Long effectivePolicyVersion() {
    NamespaceSecurityPolicyDTO effectivePolicy = effectivePolicy();
    return effectivePolicy != null ? effectivePolicy.getPolicyVersion() : null;
  }

  public @Nullable String effectivePolicyHash() {
    NamespaceSecurityPolicyDTO effectivePolicy = effectivePolicy();
    return effectivePolicy != null ? effectivePolicy.getPolicyHash() : null;
  }
}
