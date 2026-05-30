/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.client;

import io.taktx.dto.NamespaceSecurityPolicyDTO;
import java.util.Objects;

/** Focused public facet for namespace security-policy mutation operations. */
public final class SecurityClient {

  private final TaktXClient client;

  SecurityClient(TaktXClient client) {
    this.client = Objects.requireNonNull(client, "client");
  }

  /** Publishes an authoritative namespace security policy. */
  public void publishNamespaceSecurityPolicy(NamespaceSecurityPolicyDTO policy) {
    client.publishNamespaceSecurityPolicy(policy);
  }

  /** Clears the authoritative namespace security policy by publishing a tombstone. */
  public void clearNamespaceSecurityPolicy() {
    client.clearNamespaceSecurityPolicy();
  }

  /** Returns structured local availability for authoritative namespace security-policy mutation. */
  public AuthoritativePolicyMutationAvailability authoritativePolicyMutationAvailability() {
    return client.authoritativePolicyMutationAvailability();
  }
}
