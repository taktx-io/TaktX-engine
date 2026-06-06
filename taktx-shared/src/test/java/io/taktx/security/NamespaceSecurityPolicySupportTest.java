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
  void normalize_returnsPassedPolicy() {
    NamespaceSecurityPolicyDTO policy =
        NamespaceSecurityPolicyDTO.builder().mode(SecurityMode.OPEN).build();
    assertThat(NamespaceSecurityPolicySupport.normalize(policy)).isSameAs(policy);
  }

  @Test
  void normalize_nullReturnsNull() {
    assertThat(NamespaceSecurityPolicySupport.normalize(null)).isNull();
  }

  @Test
  void validationErrors_rejectMissingMode() {
    NamespaceSecurityPolicyDTO policy = NamespaceSecurityPolicyDTO.builder().build();
    assertThat(NamespaceSecurityPolicySupport.validationErrors(policy))
        .contains("mode must not be null");
  }

  @Test
  void requireValid_rejectsMissingMode() {
    NamespaceSecurityPolicyDTO policy = NamespaceSecurityPolicyDTO.builder().build();
    assertThatThrownBy(() -> NamespaceSecurityPolicySupport.requireValid(policy))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("mode must not be null");
  }

  @Test
  void requireValid_acceptsOpenPolicy() {
    NamespaceSecurityPolicyDTO policy =
        NamespaceSecurityPolicyDTO.builder().mode(SecurityMode.OPEN).build();
    NamespaceSecurityPolicyDTO validated = NamespaceSecurityPolicySupport.requireValid(policy);
    assertThat(validated.getMode()).isEqualTo(SecurityMode.OPEN);
  }

  @Test
  void requireValid_acceptsAnchoredPolicy() {
    NamespaceSecurityPolicyDTO policy =
        NamespaceSecurityPolicyDTO.builder().mode(SecurityMode.ANCHORED).build();
    NamespaceSecurityPolicyDTO validated = NamespaceSecurityPolicySupport.requireValid(policy);
    assertThat(validated.getMode()).isEqualTo(SecurityMode.ANCHORED);
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
  }
}
