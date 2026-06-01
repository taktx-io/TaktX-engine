/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.security;

import static org.assertj.core.api.Assertions.assertThat;

import io.taktx.dto.NamespaceSecurityPolicyDTO;
import io.taktx.dto.ParticipantCapability;
import io.taktx.dto.SecurityMode;
import java.util.Set;
import org.junit.jupiter.api.Test;

class NamespaceSecurityPolicyCapabilityRelevanceTest {

  @Test
  void relevantElements_returnsExpectedProtectedRuntimeMatrix() {
    assertThat(
            NamespaceSecurityPolicyCapabilityRelevance.relevantElements(
                Set.of(ParticipantCapability.PROTECTED_RUNTIME_PARTICIPANT)))
        .containsExactly(CapabilityRelevantPolicyElement.MODE);
  }

  @Test
  void relevantPolicyForCapabilities_returnsNormalizedAuthoritativePolicy() {
    NamespaceSecurityPolicyDTO policy =
        NamespaceSecurityPolicyDTO.builder()
            .mode(SecurityMode.ANCHORED)
            .policyVersion(42L)
            .build();

    NamespaceSecurityPolicyDTO relevant =
        NamespaceSecurityPolicyCapabilityRelevance.relevantPolicyForCapabilities(
            Set.of(ParticipantCapability.PROTECTED_RUNTIME_PARTICIPANT), policy);

    assertThat(relevant.getMode()).isEqualTo(SecurityMode.ANCHORED);
    assertThat(relevant.getPolicyVersion()).isEqualTo(42L);
    assertThat(relevant.getPolicyHash()).isNotBlank();
  }

  @Test
  void relevantPolicyForCapabilities_doesNotVaryByCapabilitiesInModeOnlyModel() {
    NamespaceSecurityPolicyDTO policy =
        NamespaceSecurityPolicyDTO.builder()
            .mode(SecurityMode.OPEN)
            .policyVersion(42L)
            .build();

    NamespaceSecurityPolicyDTO controlPlaneRelevant =
        NamespaceSecurityPolicyCapabilityRelevance.relevantPolicyForCapabilities(
            Set.of(
                ParticipantCapability.AUTHORITATIVE_POLICY_PUBLISHER,
                ParticipantCapability.SECURITY_OBSERVER),
            policy);
    NamespaceSecurityPolicyDTO publisherRelevant =
        NamespaceSecurityPolicyCapabilityRelevance.relevantPolicyForCapabilities(
            Set.of(ParticipantCapability.AUTHORITATIVE_POLICY_PUBLISHER), policy);
    NamespaceSecurityPolicyDTO observerRelevant =
        NamespaceSecurityPolicyCapabilityRelevance.relevantPolicyForCapabilities(
            Set.of(ParticipantCapability.SECURITY_OBSERVER), policy);

    NamespaceSecurityPolicyDTO mixedRelevant =
        NamespaceSecurityPolicyCapabilityRelevance.relevantPolicyForCapabilities(
            Set.of(
                ParticipantCapability.PROTECTED_RUNTIME_PARTICIPANT,
                ParticipantCapability.SECURITY_OBSERVER),
            policy);

    assertThat(controlPlaneRelevant).isEqualTo(publisherRelevant);
    assertThat(controlPlaneRelevant).isEqualTo(observerRelevant);
    assertThat(controlPlaneRelevant).isEqualTo(mixedRelevant);
  }
}
