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

  private final AtomicReference<NamespaceSecurityPolicyDTO> policy = new AtomicReference<>(null);

  /** Called whenever a policy record is received and validated successfully. */
  public void update(NamespaceSecurityPolicyDTO dto) {
    policy.set(dto == null ? null : NamespaceSecurityPolicySupport.requireValid(dto));
  }

  /** Clears the latest explicit policy so callers fall back to legacy/default semantics. */
  public void clear() {
    policy.set(null);
  }

  /** Returns the latest validated policy record, or {@code null} if no explicit policy exists. */
  public NamespaceSecurityPolicyDTO get() {
    return policy.get();
  }
}
