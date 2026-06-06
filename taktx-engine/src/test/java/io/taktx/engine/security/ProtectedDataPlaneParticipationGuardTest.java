/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.engine.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class ProtectedDataPlaneParticipationGuardTest {

  private MessageSigningService messageSigningService;

  @BeforeEach
  void setUp() {
    messageSigningService = Mockito.mock(MessageSigningService.class);
    when(messageSigningService.getKeyId()).thenReturn("engine-key-1");
    when(messageSigningService.isPublicKeyPublished()).thenReturn(true);
    when(messageSigningService.hasPublishableSigningIdentity()).thenReturn(true);
  }

  @Test
  void openMode_alwaysPermitted() {
    ProtectedDataPlaneParticipationGuard guard =
        new ProtectedDataPlaneParticipationGuard(false, messageSigningService);

    assertThat(guard.evaluate().permitted()).isTrue();
    assertThat(guard.evaluate().reasonHint()).isNull();
  }

  @Test
  void anchoredMode_withPublishedKey_allowsParticipation() {
    ProtectedDataPlaneParticipationGuard guard =
        new ProtectedDataPlaneParticipationGuard(true, messageSigningService);

    assertThat(guard.evaluate().permitted()).isTrue();
  }

  @Test
  void anchoredMode_withUnpublishedKey_blocksParticipation() {
    when(messageSigningService.isPublicKeyPublished()).thenReturn(false);
    ProtectedDataPlaneParticipationGuard guard =
        new ProtectedDataPlaneParticipationGuard(true, messageSigningService);

    ProtectedDataPlaneParticipationGuard.Decision decision = guard.evaluate();

    assertThat(decision.permitted()).isFalse();
    assertThat(decision.reasonHint())
        .isEqualTo(EngineSecurityReadinessEvaluator.ENGINE_SIGNING_UNAVAILABLE);
    assertThat(decision.reasonText()).contains("anchored mode");
  }
}
