/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.engine.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.taktx.dto.NamespaceSecurityPolicyDTO;
import io.taktx.dto.SecurityEventDTO;
import io.taktx.dto.SecurityEventType;
import io.taktx.dto.SecurityMode;
import io.taktx.engine.config.NamespaceSecurityPolicyStore;
import io.taktx.engine.config.ParticipantStatusStore;
import io.taktx.engine.config.TaktConfiguration;
import io.taktx.security.NamespaceSecurityPolicySupport;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

class NamespaceSecurityPolicyActivationServiceTest {

  private TaktConfiguration configuration;
  private NamespaceSecurityPolicyStore policyStore;
  private ParticipantStatusStore participantStatusStore;
  private SecurityEventPublisher securityEventPublisher;
  private Clock clock;
  private NamespaceSecurityPolicyActivationService activationService;

  @BeforeEach
  void setUp() {
    configuration = Mockito.mock(TaktConfiguration.class);
    when(configuration.getTenantId()).thenReturn("tenant");
    when(configuration.getNamespace()).thenReturn("bank.payments");
    when(configuration.getHost()).thenReturn("engine-host");
    when(configuration.getPort()).thenReturn(8080);
    when(configuration.getSecurityPolicyActivationTimeoutMs()).thenReturn(1_000L);

    policyStore = new NamespaceSecurityPolicyStore();
    participantStatusStore = new ParticipantStatusStore();
    securityEventPublisher = Mockito.mock(SecurityEventPublisher.class);
    clock = Clock.fixed(Instant.ofEpochMilli(1_716_450_000_000L), ZoneOffset.UTC);
    activationService =
        new NamespaceSecurityPolicyActivationService(
            configuration,
            policyStore,
            participantStatusStore,
            securityEventPublisher,
            clock,
            1_000L);
  }

  @Test
  void onPolicyUpdated_storesValidatedPolicyAsCurrentAndAuthoritative() {
    NamespaceSecurityPolicyDTO requested = policy(SecurityMode.ANCHORED, 42L);

    activationService.onPolicyUpdated(requested);

    assertThat(policyStore.get()).isEqualTo(requested);
    assertThat(policyStore.getAuthoritativePolicy()).isEqualTo(requested);
    verify(securityEventPublisher, never()).publish(any(SecurityEventDTO.class));
  }

  @Test
  void onPolicyCleared_removesStoredPolicy() {
    activationService.onPolicyUpdated(policy(SecurityMode.OPEN, 7L));

    activationService.onPolicyCleared();

    assertThat(policyStore.get()).isNull();
    assertThat(policyStore.getAuthoritativePolicy()).isNull();
  }

  @Test
  void onRejectedPolicyMutation_publishesRejectedEvent() {
    activationService.onPolicyUpdated(policy(SecurityMode.OPEN, 7L));

    activationService.onRejectedPolicyMutation("bad mutation", "policy");

    ArgumentCaptor<SecurityEventDTO> captor = ArgumentCaptor.forClass(SecurityEventDTO.class);
    verify(securityEventPublisher).publish(captor.capture());
    assertThat(captor.getValue().getEventType())
        .isEqualTo(SecurityEventType.CONTROL_PLANE_MUTATION_REJECTED);
    assertThat(captor.getValue().getCode())
        .isEqualTo(NamespaceSecurityPolicyActivationService.INVALID_POLICY_MUTATION_CODE);
    assertThat(captor.getValue().getDesiredPolicyVersion()).isEqualTo(7L);
    assertThat(captor.getValue().getMetadata()).containsEntry("recordKey", "policy");
  }

  private static NamespaceSecurityPolicyDTO policy(SecurityMode mode, long version) {
    return NamespaceSecurityPolicySupport.requireValid(
        NamespaceSecurityPolicyDTO.builder().mode(mode).policyVersion(version).build());
  }
}
