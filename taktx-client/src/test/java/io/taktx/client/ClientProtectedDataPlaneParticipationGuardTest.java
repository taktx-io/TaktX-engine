/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.client;

import static org.assertj.core.api.Assertions.assertThat;

import io.taktx.dto.ParticipantCapability;
import io.taktx.dto.ParticipantKind;
import io.taktx.dto.SecurityParticipantDescriptor;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ClientProtectedDataPlaneParticipationGuardTest {

  private Clock clock;

  @BeforeEach
  void setUp() {
    clock = Clock.fixed(Instant.parse("2026-05-24T10:15:30Z"), ZoneOffset.UTC);
  }

  @Test
  void evaluate_permitsOpenTrafficWhenNotAnchored() {
    ClientProtectedDataPlaneParticipationGuard guard =
        new ClientProtectedDataPlaneParticipationGuard(
            false,
            runtimeDescriptor(),
            () -> false,
            clock);

    ClientProtectedDataPlaneParticipationGuard.Decision decision =
        guard.evaluate(ProtectedClientDataPlaneOperation.START_COMMAND, null);

    assertThat(decision.permitted()).isTrue();
    assertThat(decision.reasonHint()).isNull();
    assertThat(decision.reasonText()).isNull();
  }

  @Test
  void evaluate_allowsAnchoredStartCommandWhenSigningIsReady() {
    ClientProtectedDataPlaneParticipationGuard guard =
        new ClientProtectedDataPlaneParticipationGuard(
            true,
            runtimeDescriptor(),
            () -> true,
            clock);

    ClientProtectedDataPlaneParticipationGuard.Decision decision =
        guard.evaluate(ProtectedClientDataPlaneOperation.START_COMMAND, null);

    assertThat(decision.permitted()).isTrue();
  }

  @Test
  void evaluate_allowsExternalTaskConsumptionWhenAnchoredSigningIsReady() {
    ClientProtectedDataPlaneParticipationGuard guard =
        new ClientProtectedDataPlaneParticipationGuard(
            true,
            runtimeDescriptor(),
            () -> true,
            clock);

    ClientProtectedDataPlaneParticipationGuard.Decision decision =
        guard.evaluate(ProtectedClientDataPlaneOperation.EXTERNAL_TASK_CONSUME, null);

    assertThat(decision.permitted()).isTrue();
  }

  @Test
  void evaluate_blocksClientCommandsWhenAnchoredButSigningNotReady() {
    ClientProtectedDataPlaneParticipationGuard guard =
        new ClientProtectedDataPlaneParticipationGuard(
            true,
            runtimeDescriptor(),
            () -> false,
            clock);

    ClientProtectedDataPlaneParticipationGuard.Decision decision =
        guard.evaluate(ProtectedClientDataPlaneOperation.CLIENT_COMMAND, null);

    assertThat(decision.permitted()).isFalse();
    assertThat(decision.reasonHint())
        .isEqualTo(ClientProtectedDataPlaneParticipationGuard.CLIENT_COMMAND_SIGNING_UNAVAILABLE);
  }

  @Test
  void evaluate_blocksWorkerResponseWhenAnchoredButSigningNotReady() {
    ClientProtectedDataPlaneParticipationGuard guard =
        new ClientProtectedDataPlaneParticipationGuard(
            true,
            runtimeDescriptor(),
            () -> false,
            clock);

    ClientProtectedDataPlaneParticipationGuard.Decision decision =
        guard.evaluate(ProtectedClientDataPlaneOperation.MESSAGE_EVENT, null);

    assertThat(decision.permitted()).isFalse();
    assertThat(decision.reasonHint())
        .isEqualTo(ClientProtectedDataPlaneParticipationGuard.WORKER_RESPONSE_SIGNING_UNAVAILABLE);
  }

  @Test
  void evaluate_blocksProtectedRuntimeTrafficWhenDescriptorLacksRuntimeCapability() {
    ClientProtectedDataPlaneParticipationGuard guard =
        new ClientProtectedDataPlaneParticipationGuard(
            true,
            observerDescriptor(),
            () -> true,
            clock);

    ClientProtectedDataPlaneParticipationGuard.Decision decision =
        guard.evaluate(ProtectedClientDataPlaneOperation.MESSAGE_EVENT, null);

    assertThat(decision.permitted()).isFalse();
    assertThat(decision.reasonHint())
        .isEqualTo(ClientProtectedDataPlaneParticipationGuard.PROTECTED_RUNTIME_CAPABILITY_MISSING);
  }

  private SecurityParticipantDescriptor runtimeDescriptor() {
    return new SecurityParticipantDescriptor(
        "tenant.default.client",
        ParticipantKind.CLIENT,
        Set.of(ParticipantCapability.PROTECTED_RUNTIME_PARTICIPANT),
        "generic-client");
  }

  private SecurityParticipantDescriptor observerDescriptor() {
    return new SecurityParticipantDescriptor(
        "tenant.default.observer",
        ParticipantKind.CLIENT,
        Set.of(ParticipantCapability.SECURITY_OBSERVER),
        "observer");
  }
}
