/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.client;

import io.taktx.dto.ParticipantCapability;
import io.taktx.dto.SecurityParticipantDescriptor;
import jakarta.annotation.Nullable;
import java.time.Clock;
import java.util.function.BooleanSupplier;

/**
 * Decides whether the local client may participate in protected runtime traffic.
 *
 * <p>In OPEN mode: always permitted. In ANCHORED mode: permitted once the worker signing key has
 * been published (transient window only — fail-fast startup guarantees the identity material
 * exists). The data-plane guard does NOT need to read a policy topic; mode is startup-static.
 */
final class ClientProtectedDataPlaneParticipationGuard {

  static final String WORKER_RESPONSE_SIGNING_UNAVAILABLE = "WORKER_RESPONSE_SIGNING_UNAVAILABLE";
  static final String CLIENT_COMMAND_SIGNING_UNAVAILABLE = "CLIENT_COMMAND_SIGNING_UNAVAILABLE";
  static final String PROTECTED_RUNTIME_CAPABILITY_MISSING = "PROTECTED_RUNTIME_CAPABILITY_MISSING";

  private final boolean anchored;
  private final SecurityParticipantDescriptor participantDescriptor;
  private final BooleanSupplier signingReadySupplier;
  private final Clock clock;

  ClientProtectedDataPlaneParticipationGuard(
      boolean anchored,
      SecurityParticipantDescriptor participantDescriptor,
      BooleanSupplier signingReadySupplier,
      Clock clock) {
    this.anchored = anchored;
    this.participantDescriptor = participantDescriptor;
    this.signingReadySupplier = signingReadySupplier;
    this.clock = clock;
  }

  Decision evaluate(
      ProtectedClientDataPlaneOperation operation, @Nullable String explicitAuthorizationToken) {
    if (!participantDescriptor
        .capabilities()
        .contains(ParticipantCapability.PROTECTED_RUNTIME_PARTICIPANT)) {
      return Decision.blocked(
          PROTECTED_RUNTIME_CAPABILITY_MISSING,
          "Participant descriptor "
              + participantDescriptor.participantId()
              + " does not declare PROTECTED_RUNTIME_PARTICIPANT and therefore cannot perform"
              + " protected runtime operation "
              + operation.name());
    }
    if (!anchored) {
      return Decision.permit();
    }
    // Anchored mode: permit only once signing key is published (transient window).
    if (signingReadySupplier.getAsBoolean()) {
      return Decision.permit();
    }
    String code =
        switch (operation) {
          case START_COMMAND, CLIENT_COMMAND -> CLIENT_COMMAND_SIGNING_UNAVAILABLE;
          default -> WORKER_RESPONSE_SIGNING_UNAVAILABLE;
        };
    return Decision.blocked(
        code, "Anchored mode active but client signing key is not yet published");
  }

  void check(
      ProtectedClientDataPlaneOperation operation, @Nullable String explicitAuthorizationToken) {
    Decision decision = evaluate(operation, explicitAuthorizationToken);
    if (!decision.permitted()) {
      throw new IllegalStateException(decision.reasonText());
    }
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
