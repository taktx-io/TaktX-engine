/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.client;

import io.taktx.dto.NamespaceSecurityPolicyDTO;
import io.taktx.dto.ParticipantCapability;
import io.taktx.dto.ParticipantEffectiveState;
import io.taktx.dto.ParticipantStatusDTO;
import io.taktx.dto.PolicyMismatchReasonDTO;
import io.taktx.dto.RequiredAuthorizationDTO;
import io.taktx.dto.RequiredSigningDTO;
import io.taktx.dto.SecurityActivationState;
import io.taktx.dto.SecurityMode;
import io.taktx.dto.SecurityParticipantDescriptor;
import io.taktx.dto.SecurityPostureIssueCodes;
import io.taktx.dto.StatusVerificationLevel;
import io.taktx.security.ParticipantStatusSupport;
import io.taktx.security.SecurityParticipantDescriptorSupport;
import io.taktx.security.SigningIdentity;
import io.taktx.util.TaktPropertiesHelper;
import jakarta.annotation.Nullable;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * Decides whether the local client may participate in protected runtime traffic for the current
 * authoritative namespace security policy.
 */
final class ClientProtectedDataPlaneParticipationGuard {

  static final String POLICY_NOT_ACTIVE_HINT = "POLICY_NOT_ACTIVE";
  static final String POLICY_NOT_READY_HINT = "SECURITY_POLICY_NOT_READY";
  static final String POLICY_MARKED_MISCONFIGURED = "POLICY_MARKED_MISCONFIGURED";
  static final String TRUST_ANCHOR_MISSING = SecurityPostureIssueCodes.TRUST_ANCHOR_MISSING;
  static final String CLIENT_COMMAND_SIGNING_UNAVAILABLE = "CLIENT_COMMAND_SIGNING_UNAVAILABLE";
  static final String WORKER_RESPONSE_SIGNING_UNAVAILABLE = "WORKER_RESPONSE_SIGNING_UNAVAILABLE";
  static final String START_COMMAND_AUTHORIZATION_UNAVAILABLE =
      "START_COMMAND_AUTHORIZATION_UNAVAILABLE";
  static final String EXTERNAL_TASK_AUTHORIZATION_UNAVAILABLE =
      "EXTERNAL_TASK_AUTHORIZATION_UNAVAILABLE";
  static final String USER_TASK_AUTHORIZATION_UNAVAILABLE = "USER_TASK_AUTHORIZATION_UNAVAILABLE";
  static final String PROTECTED_RUNTIME_CAPABILITY_MISSING = "PROTECTED_RUNTIME_CAPABILITY_MISSING";

  private final TaktPropertiesHelper taktPropertiesHelper;
  private final SecurityParticipantDescriptor participantDescriptor;
  private final Supplier<ClientNamespaceSecurityPolicyStore> policyStoreSupplier;
  private final Supplier<SigningIdentity> signingIdentitySupplier;
  private final BooleanSupplier signingReadySupplier;
  private final BooleanSupplier authorizationTokenProviderAvailableSupplier;
  private final Supplier<String> platformPublicKeySupplier;
  private final Clock clock;
  private final long startedAtMs;

  ClientProtectedDataPlaneParticipationGuard(
      TaktPropertiesHelper taktPropertiesHelper,
      SecurityParticipantDescriptor participantDescriptor,
      Supplier<ClientNamespaceSecurityPolicyStore> policyStoreSupplier,
      Supplier<SigningIdentity> signingIdentitySupplier,
      BooleanSupplier signingReadySupplier,
      BooleanSupplier authorizationTokenProviderAvailableSupplier,
      Supplier<String> platformPublicKeySupplier,
      Clock clock) {
    this.taktPropertiesHelper = taktPropertiesHelper;
    this.participantDescriptor =
        SecurityParticipantDescriptorSupport.requireValid(participantDescriptor);
    this.policyStoreSupplier = policyStoreSupplier;
    this.signingIdentitySupplier = signingIdentitySupplier;
    this.signingReadySupplier = signingReadySupplier;
    this.authorizationTokenProviderAvailableSupplier = authorizationTokenProviderAvailableSupplier;
    this.platformPublicKeySupplier = platformPublicKeySupplier;
    this.clock = clock;
    this.startedAtMs = clock.millis();
  }

  Decision evaluate(
      ProtectedClientDataPlaneOperation operation, @Nullable String explicitAuthorizationToken) {
    ClientNamespaceSecurityPolicyStore policyStore = policyStoreSupplier.get();
    if (policyStore == null) {
      return Decision.permit();
    }

    NamespaceSecurityPolicyDTO currentPolicy = policyStore.get();
    NamespaceSecurityPolicyDTO authoritativePolicy = policyStore.getAuthoritativePolicy();
    if (authoritativePolicy == null) {
      if (currentPolicy != null
          && currentPolicy.getActivationState() != SecurityActivationState.ACTIVE) {
        return Decision.blocked(
            POLICY_NOT_ACTIVE_HINT,
            "Protected data-plane participation is blocked until the requested namespace"
                + " security policy becomes ACTIVE");
      }
      return Decision.permit();
    }

    ParticipantStatusDTO status =
        evaluateCurrentStatus(authoritativePolicy, operation, explicitAuthorizationToken);
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
        firstMismatch != null && !isBlank(firstMismatch.getCode())
            ? firstMismatch.getCode()
            : POLICY_NOT_READY_HINT,
        firstMismatch != null && !isBlank(firstMismatch.getMessage())
            ? firstMismatch.getMessage()
            : "Protected data-plane participation is blocked because the client is not READY"
                + " for the authoritative namespace security policy");
  }

  void check(
      ProtectedClientDataPlaneOperation operation, @Nullable String explicitAuthorizationToken) {
    Decision decision = evaluate(operation, explicitAuthorizationToken);
    if (!decision.permitted()) {
      throw new IllegalStateException(decision.reasonText());
    }
  }

  ParticipantStatusDTO evaluateCurrentStatus(
      NamespaceSecurityPolicyDTO policy,
      ProtectedClientDataPlaneOperation operation,
      @Nullable String explicitAuthorizationToken) {
    long nowMs = clock.millis();
    List<PolicyMismatchReasonDTO> mismatchReasons = new ArrayList<>();
    ParticipantEffectiveState effectiveState = ParticipantEffectiveState.READY;
    boolean readyForDataPlane = true;

    if (!participantDescriptor
        .capabilities()
        .contains(ParticipantCapability.PROTECTED_RUNTIME_PARTICIPANT)) {
      effectiveState = ParticipantEffectiveState.MISMATCH;
      readyForDataPlane = false;
      mismatchReasons.add(
          mismatchReason(
              PROTECTED_RUNTIME_CAPABILITY_MISSING,
              "Participant descriptor "
                  + participantDescriptor.participantId()
                  + " does not declare PROTECTED_RUNTIME_PARTICIPANT and therefore cannot"
                  + " perform protected runtime operation "
                  + operation.name()));
    }

    if (policy.getMode() == SecurityMode.MISCONFIGURED_SECURITY) {
      effectiveState = ParticipantEffectiveState.MISMATCH;
      readyForDataPlane = false;
      mismatchReasons.add(
          mismatchReason(
              POLICY_MARKED_MISCONFIGURED,
              "Policy mode is MISCONFIGURED_SECURITY and therefore cannot be treated as ready"));
    }

    if (policy.isTrustAnchorRequired() && isBlank(platformPublicKeySupplier.get())) {
      effectiveState = ParticipantEffectiveState.MISMATCH;
      readyForDataPlane = false;
      mismatchReasons.add(
          mismatchReason(
              TRUST_ANCHOR_MISSING,
              "Namespace requires anchored trust but no platform public key is configured"));
    }

    RequiredSigningDTO requiredSigning =
        policy.getRequiredSigning() != null
            ? policy.getRequiredSigning()
            : RequiredSigningDTO.builder().build();
    RequiredAuthorizationDTO requiredAuthorization =
        policy.getRequiredAuthorization() != null
            ? policy.getRequiredAuthorization()
            : RequiredAuthorizationDTO.builder().build();

    boolean signingReady = hasSigningReadyCapability();
    boolean authorizationAvailable = hasAuthorization(explicitAuthorizationToken);
    boolean taskCompletionAuthorizationAvailable = authorizationAvailable || signingReady;

    switch (operation) {
      case START_COMMAND -> {
        if (requiredSigning.isClientCommands() && !signingReady) {
          effectiveState = ParticipantEffectiveState.MISMATCH;
          readyForDataPlane = false;
          mismatchReasons.add(
              mismatchReason(
                  CLIENT_COMMAND_SIGNING_UNAVAILABLE,
                  "Namespace requires signed client commands but no publishable client signing"
                      + " identity is ready"));
        }
        if (requiredAuthorization.isStartCommands() && !authorizationAvailable) {
          effectiveState = ParticipantEffectiveState.MISMATCH;
          readyForDataPlane = false;
          mismatchReasons.add(
              mismatchReason(
                  START_COMMAND_AUTHORIZATION_UNAVAILABLE,
                  "Namespace requires JWT authorization for start commands but no explicit token"
                      + " or AuthorizationTokenProvider is available"));
        }
      }
      case CLIENT_COMMAND -> {
        if (requiredSigning.isClientCommands() && !signingReady) {
          effectiveState = ParticipantEffectiveState.MISMATCH;
          readyForDataPlane = false;
          mismatchReasons.add(
              mismatchReason(
                  CLIENT_COMMAND_SIGNING_UNAVAILABLE,
                  "Namespace requires signed client commands but no publishable client signing"
                      + " identity is ready"));
        }
      }
      case EXTERNAL_TASK_RESPONSE, EXTERNAL_TASK_CONSUME -> {
        if (requiredSigning.isWorkerResponses() && !signingReady) {
          effectiveState = ParticipantEffectiveState.MISMATCH;
          readyForDataPlane = false;
          mismatchReasons.add(
              mismatchReason(
                  WORKER_RESPONSE_SIGNING_UNAVAILABLE,
                  "Namespace requires signed worker responses but no publishable worker signing"
                      + " identity is ready"));
        }
        if (requiredAuthorization.isExternalTaskCompletion()
            && !taskCompletionAuthorizationAvailable) {
          effectiveState = ParticipantEffectiveState.MISMATCH;
          readyForDataPlane = false;
          mismatchReasons.add(
              mismatchReason(
                  EXTERNAL_TASK_AUTHORIZATION_UNAVAILABLE,
                  "Namespace requires authorized external-task completion but neither a JWT"
                      + " source nor a signing-ready worker identity is available"));
        }
      }
      case USER_TASK_RESPONSE, USER_TASK_CONSUME -> {
        if (requiredSigning.isWorkerResponses() && !signingReady) {
          effectiveState = ParticipantEffectiveState.MISMATCH;
          readyForDataPlane = false;
          mismatchReasons.add(
              mismatchReason(
                  WORKER_RESPONSE_SIGNING_UNAVAILABLE,
                  "Namespace requires signed worker responses but no publishable worker signing"
                      + " identity is ready"));
        }
        if (requiredAuthorization.isUserTaskCompletion() && !taskCompletionAuthorizationAvailable) {
          effectiveState = ParticipantEffectiveState.MISMATCH;
          readyForDataPlane = false;
          mismatchReasons.add(
              mismatchReason(
                  USER_TASK_AUTHORIZATION_UNAVAILABLE,
                  "Namespace requires authorized user-task completion but neither a JWT source"
                      + " nor a signing-ready worker identity is available"));
        }
      }
      case MESSAGE_EVENT, SIGNAL_EVENT -> {
        // Pending/active policy state and shared trust-anchor readiness are the only current
        // client-local preconditions for these ingress event paths.
      }
    }

    return ParticipantStatusDTO.builder()
        .participantId(participantDescriptor.participantId())
        .participantInstanceId(participantInstanceId())
        .participantKind(participantDescriptor.kind())
        .componentType(participantDescriptor.componentType())
        .capabilities(participantDescriptor.capabilities())
        .supportedModes(
            ParticipantStatusSupport.supportedModesForCapabilities(
                participantDescriptor.capabilities()))
        .namespace(taktPropertiesHelper.getNamespace())
        .startedAt(startedAtMs)
        .lastSeenAt(nowMs)
        .statusExpiresAt(nowMs + 30_000L)
        .statusVerificationLevel(StatusVerificationLevel.LOCALLY_VERIFIED_STATUS)
        .effectiveState(effectiveState)
        .readyForDataPlane(readyForDataPlane)
        .observedPolicyVersion(policy.getActivePolicyVersion())
        .observedPolicyHash(policy.getActivePolicyHash())
        .mismatchReasons(List.copyOf(mismatchReasons))
        .build();
  }

  private boolean hasSigningReadyCapability() {
    SigningIdentity identity = signingIdentitySupplier.get();
    return identity != null && signingReadySupplier.getAsBoolean();
  }

  private boolean hasAuthorization(@Nullable String explicitAuthorizationToken) {
    return !isBlank(explicitAuthorizationToken)
        || authorizationTokenProviderAvailableSupplier.getAsBoolean();
  }

  private String participantId() {
    return participantDescriptor.participantId();
  }

  private String participantInstanceId() {
    return participantId() + "#" + ProcessHandle.current().pid();
  }

  private static PolicyMismatchReasonDTO mismatchReason(String code, String message) {
    return PolicyMismatchReasonDTO.builder().code(code).message(message).build();
  }

  private static boolean isBlank(@Nullable String value) {
    return value == null || value.isBlank();
  }

  record Decision(boolean permitted, @Nullable String reasonHint, @Nullable String reasonText) {

    static Decision permit() {
      return new Decision(true, null, null);
    }

    static Decision blocked(String reasonHint, String reasonText) {
      return new Decision(false, reasonHint, reasonText);
    }
  }
}
