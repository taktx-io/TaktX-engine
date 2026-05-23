/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.security;

import static org.assertj.core.api.Assertions.assertThat;

import io.taktx.dto.SecurityActivationState;
import org.junit.jupiter.api.Test;

class NamespaceSecurityPolicyActivationAuthorityContractTest {

  @Test
  void soleActivationAuthority_isPlatformServiceForFirstSlice() {
    assertThat(NamespaceSecurityPolicyActivationAuthorityContract.soleActivationAuthority())
        .isEqualTo(NamespaceSecurityPolicyActivationAuthority.PLATFORM_SERVICE);
  }

  @Test
  void onlyPlatformServiceMayTransitionPolicyIntoActive() {
    assertThat(
            NamespaceSecurityPolicyActivationAuthorityContract.mayTransitionActivationState(
                NamespaceSecurityPolicyActivationAuthority.PLATFORM_SERVICE,
                SecurityActivationState.VALIDATING,
                SecurityActivationState.ACTIVE))
        .isTrue();
    assertThat(
            NamespaceSecurityPolicyActivationAuthorityContract.mayTransitionActivationState(
                NamespaceSecurityPolicyActivationAuthority.PARTICIPANT_RUNTIME,
                SecurityActivationState.VALIDATING,
                SecurityActivationState.ACTIVE))
        .isFalse();
    assertThat(
            NamespaceSecurityPolicyActivationAuthorityContract.mayTransitionActivationState(
                NamespaceSecurityPolicyActivationAuthority.UNKNOWN,
                SecurityActivationState.REQUESTED,
                SecurityActivationState.ACTIVE))
        .isFalse();
  }

  @Test
  void nonActiveTransitionsRemainNonAuthoritativeControlPlaneCompatible() {
    assertThat(
            NamespaceSecurityPolicyActivationAuthorityContract.mayTransitionActivationState(
                NamespaceSecurityPolicyActivationAuthority.PARTICIPANT_RUNTIME,
                SecurityActivationState.REQUESTED,
                SecurityActivationState.VALIDATING))
        .isTrue();
    assertThat(
            NamespaceSecurityPolicyActivationAuthorityContract.nonAuthoritativeParticipants())
        .contains(
            NamespaceSecurityPolicyActivationAuthority.PARTICIPANT_RUNTIME,
            NamespaceSecurityPolicyActivationAuthority.UNKNOWN);
  }
}

