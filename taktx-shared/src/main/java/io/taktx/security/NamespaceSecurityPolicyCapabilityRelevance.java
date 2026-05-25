/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.security;

import io.taktx.dto.NamespaceSecurityPolicyDTO;
import io.taktx.dto.ParticipantCapability;
import io.taktx.dto.RequiredAuthorizationDTO;
import io.taktx.dto.RequiredSigningDTO;
import java.util.EnumSet;
import java.util.Set;

/** Shared mapping of capability-relevant namespace policy elements for readiness and gating. */
public final class NamespaceSecurityPolicyCapabilityRelevance {

  private NamespaceSecurityPolicyCapabilityRelevance() {}

  public static Set<CapabilityRelevantPolicyElement> relevantElements(
      Set<ParticipantCapability> capabilities) {
    EnumSet<CapabilityRelevantPolicyElement> relevant =
        EnumSet.of(
            CapabilityRelevantPolicyElement.MODE,
            CapabilityRelevantPolicyElement.TRUST_ANCHOR_REQUIRED);
    if (capabilities == null || capabilities.isEmpty()) {
      return Set.copyOf(relevant);
    }
    if (capabilities.contains(ParticipantCapability.PROTECTED_RUNTIME_PARTICIPANT)
        || capabilities.contains(ParticipantCapability.ENFORCER)) {
      relevant.add(CapabilityRelevantPolicyElement.REQUIRED_SIGNING_CLIENT_COMMANDS);
      relevant.add(CapabilityRelevantPolicyElement.REQUIRED_AUTHORIZATION_START_COMMANDS);
      relevant.add(CapabilityRelevantPolicyElement.REQUIRED_SIGNING_WORKER_RESPONSES);
      relevant.add(CapabilityRelevantPolicyElement.REQUIRED_AUTHORIZATION_EXTERNAL_TASK_COMPLETION);
      relevant.add(CapabilityRelevantPolicyElement.REQUIRED_AUTHORIZATION_USER_TASK_COMPLETION);
    }
    return Set.copyOf(relevant);
  }

  /**
   * Returns a normalized policy view containing only fields relevant to the supplied capabilities.
   */
  public static NamespaceSecurityPolicyDTO relevantPolicyForCapabilities(
      Set<ParticipantCapability> capabilities, NamespaceSecurityPolicyDTO policy) {
    if (policy == null) {
      return null;
    }
    NamespaceSecurityPolicyDTO normalized = NamespaceSecurityPolicySupport.normalize(policy);
    Set<CapabilityRelevantPolicyElement> relevant = relevantElements(capabilities);

    RequiredSigningDTO signing =
        RequiredSigningDTO.builder()
            .clientCommands(
                relevant.contains(CapabilityRelevantPolicyElement.REQUIRED_SIGNING_CLIENT_COMMANDS)
                    && normalized.getRequiredSigning().isClientCommands())
            .workerResponses(
                relevant.contains(CapabilityRelevantPolicyElement.REQUIRED_SIGNING_WORKER_RESPONSES)
                    && normalized.getRequiredSigning().isWorkerResponses())
            .build();

    RequiredAuthorizationDTO authorization =
        RequiredAuthorizationDTO.builder()
            .startCommands(
                relevant.contains(
                        CapabilityRelevantPolicyElement.REQUIRED_AUTHORIZATION_START_COMMANDS)
                    && normalized.getRequiredAuthorization().isStartCommands())
            .externalTaskCompletion(
                relevant.contains(
                        CapabilityRelevantPolicyElement
                            .REQUIRED_AUTHORIZATION_EXTERNAL_TASK_COMPLETION)
                    && normalized.getRequiredAuthorization().isExternalTaskCompletion())
            .userTaskCompletion(
                relevant.contains(
                        CapabilityRelevantPolicyElement.REQUIRED_AUTHORIZATION_USER_TASK_COMPLETION)
                    && normalized.getRequiredAuthorization().isUserTaskCompletion())
            .build();

    return normalized.toBuilder()
        .requiredSigning(signing)
        .requiredAuthorization(authorization)
        .trustAnchorRequired(
            relevant.contains(CapabilityRelevantPolicyElement.TRUST_ANCHOR_REQUIRED)
                && normalized.isTrustAnchorRequired())
        .build();
  }
}
