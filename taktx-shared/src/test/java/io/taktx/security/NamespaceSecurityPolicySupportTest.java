/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.taktx.dto.NamespaceSecurityPolicyDTO;
import io.taktx.dto.SecurityMode;
import org.junit.jupiter.api.Test;

class NamespaceSecurityPolicySupportTest {

  @Test
  void normalize_computesCanonicalPolicyHashWhenMissing() {
    NamespaceSecurityPolicyDTO policy =
        NamespaceSecurityPolicyDTO.builder().mode(SecurityMode.OPEN).policyVersion(42L).build();

    NamespaceSecurityPolicyDTO normalized = NamespaceSecurityPolicySupport.normalize(policy);

    assertThat(normalized.getPolicyVersion()).isEqualTo(42L);
    assertThat(normalized.getPolicyHash()).isNotBlank();
  }

  @Test
  void canonicalHash_dependsOnlyOnAuthoritativePolicyContent() {
    NamespaceSecurityPolicyDTO baseline =
        NamespaceSecurityPolicyDTO.builder().mode(SecurityMode.ANCHORED).policyVersion(5L).build();

    NamespaceSecurityPolicyDTO sameEffectiveContentDifferentHashWrapper =
        baseline.toBuilder().policyHash("manually-supplied-hash").build();

    assertThat(NamespaceSecurityPolicySupport.canonicalHash(baseline))
        .isEqualTo(
            NamespaceSecurityPolicySupport.canonicalHash(sameEffectiveContentDifferentHashWrapper));
  }

  @Test
  void validationErrors_rejectMissingMode() {
    NamespaceSecurityPolicyDTO policy =
        NamespaceSecurityPolicyDTO.builder().policyVersion(1L).build();

    assertThat(NamespaceSecurityPolicySupport.validationErrors(policy))
        .contains("mode must not be null");
  }

  @Test
  void requireValid_rejectsMissingPolicyVersion() {
    NamespaceSecurityPolicyDTO policy =
        NamespaceSecurityPolicyDTO.builder().mode(SecurityMode.OPEN).build();

    assertThatThrownBy(() -> NamespaceSecurityPolicySupport.requireValid(policy))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("policyVersion must not be null");
  }

  @Test
  void requireValid_acceptsOpenPolicyAndComputesHash() {
    NamespaceSecurityPolicyDTO policy =
        NamespaceSecurityPolicyDTO.builder().mode(SecurityMode.OPEN).policyVersion(10L).build();

    NamespaceSecurityPolicyDTO validated = NamespaceSecurityPolicySupport.requireValid(policy);

    assertThat(validated.getPolicyHash()).isNotBlank();
  }

  @Test
  void requireValid_acceptsAnchoredPolicyPayload() {
    NamespaceSecurityPolicyDTO policy =
        NamespaceSecurityPolicyDTO.builder().mode(SecurityMode.ANCHORED).policyVersion(11L).build();

    NamespaceSecurityPolicyDTO validated = NamespaceSecurityPolicySupport.requireValid(policy);

    assertThat(validated.getMode()).isEqualTo(SecurityMode.ANCHORED);
    assertThat(validated.getPolicyVersion()).isEqualTo(11L);
    assertThat(validated.getPolicyHash()).isNotBlank();
  }

  @Test
  void requireValid_rejectsNonPositivePolicyVersion() {
    NamespaceSecurityPolicyDTO policy =
        NamespaceSecurityPolicyDTO.builder().mode(SecurityMode.OPEN).policyVersion(0L).build();

    assertThatThrownBy(() -> NamespaceSecurityPolicySupport.requireValid(policy))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("policyVersion must be > 0");
  }

  @Test
  void parseSecurityMode_acceptsCommonOperatorFormats() {
    assertThat(NamespaceSecurityPolicySupport.parseSecurityMode("open"))
        .isEqualTo(SecurityMode.OPEN);
    assertThat(NamespaceSecurityPolicySupport.parseSecurityMode("anchored"))
        .isEqualTo(SecurityMode.ANCHORED);
  }

  @Test
  void parseHelpers_rejectUnsupportedModes() {
    assertThatThrownBy(() -> NamespaceSecurityPolicySupport.parseSecurityMode("secured"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Unsupported security mode");
    assertThatThrownBy(() -> NamespaceSecurityPolicySupport.parseSecurityMode("anchored secured"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Unsupported security mode");
  }
}
