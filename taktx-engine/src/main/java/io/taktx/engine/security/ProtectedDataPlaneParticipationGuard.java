/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.engine.security;

import io.taktx.dto.NamespaceSecurityPolicyDTO;
import io.taktx.dto.ParticipantStatusDTO;
import io.taktx.dto.PolicyMismatchReasonDTO;
import io.taktx.dto.SecurityActivationState;
import io.taktx.engine.config.NamespaceSecurityPolicyStore;
import io.taktx.security.ParticipantStatusSupport;
import java.time.Clock;

/**
 * Decides whether the local engine may participate in protected data-plane processing.
 *
 * <p>Control-plane traffic remains available while policy is pending or participants are converging,
 * but protected runtime work must fail closed unless there is either no explicit authoritative
 * policy (default community-open) or the engine is READY for the exact authoritative active policy
 * identity.
 */
public class ProtectedDataPlaneParticipationGuard {

  public static final String POLICY_NOT_ACTIVE_HINT = "POLICY_NOT_ACTIVE";
  public static final String POLICY_NOT_READY_HINT = "SECURITY_POLICY_NOT_READY";

  private final NamespaceSecurityPolicyStore namespaceSecurityPolicyStore;
  private final EngineSecurityReadinessEvaluator readinessEvaluator;
  private final Clock clock;

  public ProtectedDataPlaneParticipationGuard(
      NamespaceSecurityPolicyStore namespaceSecurityPolicyStore,
      EngineSecurityReadinessEvaluator readinessEvaluator,
      Clock clock) {
    this.namespaceSecurityPolicyStore = namespaceSecurityPolicyStore;
    this.readinessEvaluator = readinessEvaluator;
    this.clock = clock;
  }

  public Decision evaluate() {
    NamespaceSecurityPolicyDTO currentPolicy = namespaceSecurityPolicyStore.get();
    NamespaceSecurityPolicyDTO authoritativePolicy = namespaceSecurityPolicyStore.getAuthoritativePolicy();

    if (authoritativePolicy == null) {
      if (currentPolicy != null && currentPolicy.getActivationState() != SecurityActivationState.ACTIVE) {
        return Decision.blocked(
            POLICY_NOT_ACTIVE_HINT,
            "Protected data-plane participation is blocked until the requested namespace security policy becomes ACTIVE");
      }
      return Decision.permit();
    }

    ParticipantStatusDTO status = readinessEvaluator.evaluateCurrentStatus();
    if (ParticipantStatusSupport.allowsProtectedDataPlaneParticipation(
        status,
        authoritativePolicy.getActivePolicyVersion(),
        authoritativePolicy.getActivePolicyHash(),
        clock.millis())) {
      return Decision.permit();
    }

    PolicyMismatchReasonDTO firstMismatch =
        status.getMismatchReasons() == null || status.getMismatchReasons().isEmpty()
            ? null
            : status.getMismatchReasons().getFirst();
    return Decision.blocked(
        firstMismatch != null && firstMismatch.getCode() != null && !firstMismatch.getCode().isBlank()
            ? firstMismatch.getCode()
            : POLICY_NOT_READY_HINT,
        firstMismatch != null && firstMismatch.getMessage() != null && !firstMismatch.getMessage().isBlank()
            ? firstMismatch.getMessage()
            : "Protected data-plane participation is blocked because the engine is not READY for the authoritative namespace security policy");
  }

  public record Decision(boolean permitted, String reasonHint, String reasonText) {

    public static Decision permit() {
      return new Decision(true, null, null);
    }

    public static Decision blocked(String reasonHint, String reasonText) {
      return new Decision(false, reasonHint, reasonText);
    }
  }
}



