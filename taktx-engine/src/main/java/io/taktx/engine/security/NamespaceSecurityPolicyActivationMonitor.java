/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.engine.security;

import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;

/** Periodically reevaluates namespace policy activation so timeout semantics are enforced. */
@ApplicationScoped
public class NamespaceSecurityPolicyActivationMonitor {

  private final NamespaceSecurityPolicyActivationService activationService;

  public NamespaceSecurityPolicyActivationMonitor(
      NamespaceSecurityPolicyActivationService activationService) {
    this.activationService = activationService;
  }

  @Scheduled(
      every = "${taktx.security.policy.activation-monitor-interval:1s}",
      concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
  void reevaluate() {
    activationService.reevaluate();
  }
}

