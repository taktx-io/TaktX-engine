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
import io.taktx.dto.SecurityActivationState;
import io.taktx.dto.SecurityMode;
import org.junit.jupiter.api.Test;

class NamespaceSecurityPolicyControlPlaneContractTest {

  @Test
  void baselineContract_requiresTrustedAuthoritativeWriterProperties() {
    assertThat(NamespaceSecurityPolicyControlPlaneContract.policyRecordKey()).isEqualTo("policy");
    assertThat(NamespaceSecurityPolicyControlPlaneContract.requiredWriterSecurityProperties())
        .containsExactlyInAnyOrder(
            AuthoritativeControlPlaneSecurityProperty.BROKER_AUTHORIZATION_REQUIRED,
            AuthoritativeControlPlaneSecurityProperty.TRUSTED_WRITER_PATH_ONLY,
            AuthoritativeControlPlaneSecurityProperty.FIXED_RECORD_KEY_REQUIRED);
  }

  @Test
  void securedPolicy_requiresIntegrityProtectionProperty() {
    NamespaceSecurityPolicyDTO policy =
        NamespaceSecurityPolicyDTO.builder()
            .mode(SecurityMode.SECURED)
            .activationState(SecurityActivationState.REQUESTED)
            .desiredPolicyVersion(42L)
            .build();

    assertThat(NamespaceSecurityPolicyControlPlaneContract.requiredWriterSecurityProperties(policy))
        .contains(
            AuthoritativeControlPlaneSecurityProperty
                .INTEGRITY_PROTECTION_REQUIRED_IN_SECURED_MODES);
  }

  @Test
  void breakGlassMetadata_requiresBreakGlassContractProperty() {
    NamespaceSecurityPolicyDTO policy =
        NamespaceSecurityPolicyDTO.builder()
            .mode(SecurityMode.SECURED)
            .activationState(SecurityActivationState.REQUESTED)
            .desiredPolicyVersion(42L)
            .breakGlassActor("ops-admin")
            .breakGlassReason("containment downgrade")
            .build();

    assertThat(NamespaceSecurityPolicyControlPlaneContract.requiredWriterSecurityProperties(policy))
        .contains(
            AuthoritativeControlPlaneSecurityProperty.BREAK_GLASS_METADATA_REQUIRED_FOR_DOWNGRADE);
  }
}
