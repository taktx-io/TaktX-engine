/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.security;

import io.taktx.dto.NamespaceSecurityPolicyDTO;
import io.taktx.dto.ParticipantRole;
import io.taktx.dto.RequiredAuthorizationDTO;
import io.taktx.dto.RequiredSigningDTO;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/** Shared mapping of role-relevant namespace policy elements for readiness and gating decisions. */
public final class NamespaceSecurityPolicyRoleRelevance {

  private static final Map<ParticipantRole, Set<RoleRelevantPolicyElement>> ROLE_ELEMENTS =
      Map.of(
          ParticipantRole.ENGINE,
          Set.copyOf(
              EnumSet.of(
                  RoleRelevantPolicyElement.MODE,
                  RoleRelevantPolicyElement.TRUST_ANCHOR_REQUIRED,
                  RoleRelevantPolicyElement.REQUIRED_SIGNING_ENGINE_OUTBOUND,
                  RoleRelevantPolicyElement.REQUIRED_AUTHORIZATION_START_COMMANDS,
                  RoleRelevantPolicyElement.REQUIRED_AUTHORIZATION_EXTERNAL_TASK_COMPLETION,
                  RoleRelevantPolicyElement.REQUIRED_AUTHORIZATION_USER_TASK_COMPLETION)),
          ParticipantRole.CLIENT,
          Set.copyOf(
              EnumSet.of(
                  RoleRelevantPolicyElement.MODE,
                  RoleRelevantPolicyElement.TRUST_ANCHOR_REQUIRED,
                  RoleRelevantPolicyElement.REQUIRED_SIGNING_CLIENT_COMMANDS,
                  RoleRelevantPolicyElement.REQUIRED_AUTHORIZATION_START_COMMANDS,
                  RoleRelevantPolicyElement.REQUIRED_AUTHORIZATION_USER_TASK_COMPLETION)),
          ParticipantRole.WORKER,
          Set.copyOf(
              EnumSet.of(
                  RoleRelevantPolicyElement.MODE,
                  RoleRelevantPolicyElement.TRUST_ANCHOR_REQUIRED,
                  RoleRelevantPolicyElement.REQUIRED_SIGNING_WORKER_RESPONSES,
                  RoleRelevantPolicyElement.REQUIRED_AUTHORIZATION_EXTERNAL_TASK_COMPLETION)),
          ParticipantRole.INGESTER,
          Set.copyOf(
              EnumSet.of(
                  RoleRelevantPolicyElement.MODE, RoleRelevantPolicyElement.TRUST_ANCHOR_REQUIRED)),
          ParticipantRole.CONSOLE,
          Set.copyOf(
              EnumSet.of(
                  RoleRelevantPolicyElement.MODE, RoleRelevantPolicyElement.TRUST_ANCHOR_REQUIRED)));

  private NamespaceSecurityPolicyRoleRelevance() {}

  public static Set<RoleRelevantPolicyElement> relevantElements(ParticipantRole role) {
    if (role == null) {
      return Set.of();
    }
    return ROLE_ELEMENTS.getOrDefault(role, Set.of());
  }

  /** Returns a normalized policy view containing only the fields relevant to the supplied role. */
  public static NamespaceSecurityPolicyDTO relevantPolicyForRole(
      ParticipantRole role, NamespaceSecurityPolicyDTO policy) {
    if (policy == null) {
      return null;
    }
    NamespaceSecurityPolicyDTO normalized = NamespaceSecurityPolicySupport.normalize(policy);
    Set<RoleRelevantPolicyElement> relevant = relevantElements(role);

    RequiredSigningDTO signing =
        RequiredSigningDTO.builder()
            .engineOutbound(relevant.contains(RoleRelevantPolicyElement.REQUIRED_SIGNING_ENGINE_OUTBOUND)
                && normalized.getRequiredSigning().isEngineOutbound())
            .clientCommands(relevant.contains(RoleRelevantPolicyElement.REQUIRED_SIGNING_CLIENT_COMMANDS)
                && normalized.getRequiredSigning().isClientCommands())
            .workerResponses(relevant.contains(RoleRelevantPolicyElement.REQUIRED_SIGNING_WORKER_RESPONSES)
                && normalized.getRequiredSigning().isWorkerResponses())
            .build();

    RequiredAuthorizationDTO authorization =
        RequiredAuthorizationDTO.builder()
            .startCommands(relevant.contains(RoleRelevantPolicyElement.REQUIRED_AUTHORIZATION_START_COMMANDS)
                && normalized.getRequiredAuthorization().isStartCommands())
            .externalTaskCompletion(
                relevant.contains(RoleRelevantPolicyElement.REQUIRED_AUTHORIZATION_EXTERNAL_TASK_COMPLETION)
                    && normalized.getRequiredAuthorization().isExternalTaskCompletion())
            .userTaskCompletion(
                relevant.contains(RoleRelevantPolicyElement.REQUIRED_AUTHORIZATION_USER_TASK_COMPLETION)
                    && normalized.getRequiredAuthorization().isUserTaskCompletion())
            .build();

    return normalized.toBuilder()
        .requiredSigning(signing)
        .requiredAuthorization(authorization)
        .trustAnchorRequired(
            relevant.contains(RoleRelevantPolicyElement.TRUST_ANCHOR_REQUIRED)
                && normalized.isTrustAnchorRequired())
        .build();
  }
}

