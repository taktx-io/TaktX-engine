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
  void relevantPolicyForCapabilities_returnsPolicy() {
    NamespaceSecurityPolicyDTO policy =
        NamespaceSecurityPolicyDTO.builder().mode(SecurityMode.ANCHORED).build();

    NamespaceSecurityPolicyDTO relevant =
        NamespaceSecurityPolicyCapabilityRelevance.relevantPolicyForCapabilities(
            Set.of(ParticipantCapability.PROTECTED_RUNTIME_PARTICIPANT), policy);

    assertThat(relevant.getMode()).isEqualTo(SecurityMode.ANCHORED);
  }

  @Test
  void relevantPolicyForCapabilities_doesNotVaryByCapabilitiesInModeOnlyModel() {
    NamespaceSecurityPolicyDTO policy =
        NamespaceSecurityPolicyDTO.builder().mode(SecurityMode.OPEN).build();

    NamespaceSecurityPolicyDTO runtimeRelevant =
        NamespaceSecurityPolicyCapabilityRelevance.relevantPolicyForCapabilities(
            Set.of(ParticipantCapability.PROTECTED_RUNTIME_PARTICIPANT), policy);
    NamespaceSecurityPolicyDTO observerRelevant =
        NamespaceSecurityPolicyCapabilityRelevance.relevantPolicyForCapabilities(
            Set.of(ParticipantCapability.SECURITY_OBSERVER), policy);

    assertThat(runtimeRelevant).isEqualTo(observerRelevant);
  }
}
