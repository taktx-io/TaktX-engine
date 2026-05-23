/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.taktx.dto.GlobalConfigurationDTO;
import io.taktx.dto.NamespaceSecurityPolicyDTO;
import io.taktx.dto.RequiredAuthorizationDTO;
import io.taktx.dto.RequiredSigningDTO;
import io.taktx.dto.SecurityActivationState;
import io.taktx.dto.SecurityMode;
import io.taktx.security.AuthoritativeControlPlaneSecurityProperty;
import io.taktx.security.NamespaceSecurityPolicyActivationAuthority;
import io.taktx.serdes.NamespaceSecurityPolicyProtoMapper;
import org.junit.jupiter.api.Test;

class TaktXClientNamespaceSecurityPolicyTest {

  @Test
  void normalizeNamespaceSecurityPolicy_fillsAliasesAndCanonicalHash() {
    NamespaceSecurityPolicyDTO normalized =
        TaktXClient.normalizeNamespaceSecurityPolicy(
            NamespaceSecurityPolicyDTO.builder()
                .mode(SecurityMode.COMMUNITY_SECURED)
                .activationState(SecurityActivationState.REQUESTED)
                .policyVersion(42L)
                .requiredSigning(RequiredSigningDTO.builder().engineOutbound(true).build())
                .requiredAuthorization(
                    RequiredAuthorizationDTO.builder().startCommands(true).build())
                .build());

    assertThat(normalized.getDesiredPolicyVersion()).isEqualTo(42L);
    assertThat(normalized.getPolicyVersion()).isEqualTo(42L);
    assertThat(normalized.getDesiredPolicyHash()).isNotBlank();
    assertThat(normalized.getPolicyHash()).isEqualTo(normalized.getDesiredPolicyHash());
  }

  @Test
  void canonicalNamespaceSecurityPolicyHash_ignoresIdentityWrapperFields() {
    NamespaceSecurityPolicyDTO baseline =
        NamespaceSecurityPolicyDTO.builder()
            .mode(SecurityMode.ANCHORED_SECURED)
            .activationState(SecurityActivationState.REQUESTED)
            .desiredPolicyVersion(1L)
            .desiredPolicyHash("hash-a")
            .activePolicyVersion(7L)
            .activePolicyHash("hash-b")
            .requiredSigning(
                RequiredSigningDTO.builder()
                    .engineOutbound(true)
                    .clientCommands(true)
                    .workerResponses(true)
                    .build())
            .requiredAuthorization(
                RequiredAuthorizationDTO.builder()
                    .startCommands(true)
                    .externalTaskCompletion(true)
                    .userTaskCompletion(true)
                    .build())
            .trustAnchorRequired(true)
            .policyVersion(1L)
            .policyHash("legacy-a")
            .build();

    NamespaceSecurityPolicyDTO sameEffectivePolicy =
        baseline.toBuilder()
            .desiredPolicyVersion(2L)
            .desiredPolicyHash("hash-c")
            .activePolicyVersion(9L)
            .activePolicyHash("hash-d")
            .policyVersion(2L)
            .policyHash("legacy-b")
            .build();

    assertThat(TaktXClient.canonicalNamespaceSecurityPolicyHash(baseline))
        .isEqualTo(TaktXClient.canonicalNamespaceSecurityPolicyHash(sameEffectivePolicy));
  }

  @Test
  void validateNamespaceSecurityPolicy_rejectsInvalidAnchoredPolicy() {
    NamespaceSecurityPolicyDTO policy =
        NamespaceSecurityPolicyDTO.builder()
            .mode(SecurityMode.ANCHORED_SECURED)
            .activationState(SecurityActivationState.REQUESTED)
            .desiredPolicyVersion(1L)
            .build();

    assertThatThrownBy(() -> TaktXClient.validateNamespaceSecurityPolicy(policy))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("ANCHORED_SECURED requires trustAnchorRequired=true");
  }

  @Test
  void validateNamespaceSecurityPolicy_returnsNormalizedPolicyForValidInput() {
    NamespaceSecurityPolicyDTO validated =
        TaktXClient.validateNamespaceSecurityPolicy(
            NamespaceSecurityPolicyDTO.builder()
                .mode(SecurityMode.COMMUNITY_SECURED)
                .activationState(SecurityActivationState.ACTIVE)
                .desiredPolicyVersion(99L)
                .requiredSigning(RequiredSigningDTO.builder().engineOutbound(true).build())
                .requiredAuthorization(
                    RequiredAuthorizationDTO.builder().startCommands(true).build())
                .activePolicyVersion(99L)
                .build());

    assertThat(validated.getDesiredPolicyHash()).isNotBlank();
    assertThat(validated.getActivePolicyHash()).isEqualTo(validated.getDesiredPolicyHash());
  }

  @Test
  void validateNamespaceSecurityPolicy_rejectsPartialBreakGlassMetadata() {
    NamespaceSecurityPolicyDTO policy =
        NamespaceSecurityPolicyDTO.builder()
            .mode(SecurityMode.COMMUNITY_SECURED)
            .activationState(SecurityActivationState.REQUESTED)
            .desiredPolicyVersion(42L)
            .breakGlassActor("ops-admin")
            .build();

    assertThatThrownBy(() -> TaktXClient.validateNamespaceSecurityPolicy(policy))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("breakGlassActor and breakGlassReason must be provided together");
  }

  @Test
  void buildNamespaceSecurityPolicyRecord_usesPolicyKeyAndSerializesValidatedPolicy() throws Exception {
    NamespaceSecurityPolicyDTO input =
        NamespaceSecurityPolicyDTO.builder()
            .mode(SecurityMode.COMMUNITY_SECURED)
            .activationState(SecurityActivationState.REQUESTED)
            .desiredPolicyVersion(42L)
            .requiredSigning(RequiredSigningDTO.builder().engineOutbound(true).build())
            .build();

    var record =
        TaktXClient.buildNamespaceSecurityPolicyRecord(
            "tenant.bank.payments.taktx-security-policy", input);

    assertThat(record.topic()).isEqualTo("tenant.bank.payments.taktx-security-policy");
    assertThat(record.key()).isEqualTo(TaktXClient.NAMESPACE_SECURITY_POLICY_RECORD_KEY);
    NamespaceSecurityPolicyDTO serialized =
        NamespaceSecurityPolicyProtoMapper.toDto(
            io.taktx.proto.NamespaceSecurityPolicyMessage.parseFrom(record.value()));
    assertThat(serialized.getDesiredPolicyVersion()).isEqualTo(42L);
    assertThat(serialized.getDesiredPolicyHash()).isNotBlank();
  }

  @Test
  void buildNamespaceSecurityPolicyRecord_rejectsBlankTopic() {
    NamespaceSecurityPolicyDTO input =
        NamespaceSecurityPolicyDTO.builder()
            .mode(SecurityMode.COMMUNITY_SECURED)
            .activationState(SecurityActivationState.REQUESTED)
            .desiredPolicyVersion(42L)
            .build();

    assertThatThrownBy(() -> TaktXClient.buildNamespaceSecurityPolicyRecord(" ", input))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("topic must not be blank");
  }

  @Test
  void buildNamespaceSecurityPolicyTombstoneRecord_usesPolicyKeyAndNullValue() {
    var record =
        TaktXClient.buildNamespaceSecurityPolicyTombstoneRecord(
            "tenant.bank.payments.taktx-security-policy");

    assertThat(record.topic()).isEqualTo("tenant.bank.payments.taktx-security-policy");
    assertThat(record.key()).isEqualTo(TaktXClient.NAMESPACE_SECURITY_POLICY_RECORD_KEY);
    assertThat(record.value()).isNull();
  }

  @Test
  void buildNamespaceSecurityPolicyTombstoneRecord_rejectsBlankTopic() {
    assertThatThrownBy(() -> TaktXClient.buildNamespaceSecurityPolicyTombstoneRecord(" "))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("topic must not be blank");
  }

  @Test
  void namespaceSecurityPolicyWriterSecurityProperties_exposesAuthoritativeMutationContract() {
    assertThat(TaktXClient.namespaceSecurityPolicyWriterSecurityProperties())
        .contains(
            AuthoritativeControlPlaneSecurityProperty.BROKER_AUTHORIZATION_REQUIRED,
            AuthoritativeControlPlaneSecurityProperty.TRUSTED_WRITER_PATH_ONLY,
            AuthoritativeControlPlaneSecurityProperty.FIXED_RECORD_KEY_REQUIRED);
  }

  @Test
  void namespaceSecurityPolicyWriterSecurityProperties_forSecuredBreakGlassPolicy_addsExtraRequirements() {
    NamespaceSecurityPolicyDTO policy =
        NamespaceSecurityPolicyDTO.builder()
            .mode(SecurityMode.COMMUNITY_SECURED)
            .activationState(SecurityActivationState.REQUESTED)
            .desiredPolicyVersion(42L)
            .breakGlassActor("ops-admin")
            .breakGlassReason("containment downgrade")
            .build();

    assertThat(TaktXClient.namespaceSecurityPolicyWriterSecurityProperties(policy))
        .contains(
            AuthoritativeControlPlaneSecurityProperty.INTEGRITY_PROTECTION_REQUIRED_IN_SECURED_MODES,
            AuthoritativeControlPlaneSecurityProperty.BREAK_GLASS_METADATA_REQUIRED_FOR_DOWNGRADE);
  }

  @Test
  void namespaceSecurityPolicyActivationAuthority_exposesPlatformServiceAsSoleAuthority() {
    assertThat(TaktXClient.namespaceSecurityPolicyActivationAuthority())
        .isEqualTo(NamespaceSecurityPolicyActivationAuthority.PLATFORM_SERVICE);
  }

  @Test
  void legacyGlobalSecurityConfigToNamespaceSecurityPolicy_mapsSecuredFlags() {
    NamespaceSecurityPolicyDTO policy =
        TaktXClient.legacyGlobalSecurityConfigToNamespaceSecurityPolicy(
            GlobalConfigurationDTO.builder()
                .signingEnabled(true)
                .engineRequiresAuthorization(true)
                .engineRequiresExternalTaskAuthorization(true)
                .engineRequiresUserTaskAuthorization(false)
                .build(),
            42L);

    assertThat(policy.getMode()).isEqualTo(SecurityMode.COMMUNITY_SECURED);
    assertThat(policy.getActivationState()).isEqualTo(SecurityActivationState.REQUESTED);
    assertThat(policy.getDesiredPolicyVersion()).isEqualTo(42L);
    assertThat(policy.getRequiredSigning().isEngineOutbound()).isTrue();
    assertThat(policy.getRequiredAuthorization().isStartCommands()).isTrue();
    assertThat(policy.getRequiredAuthorization().isExternalTaskCompletion()).isTrue();
    assertThat(policy.getRequiredAuthorization().isUserTaskCompletion()).isFalse();
    assertThat(policy.isTrustAnchorRequired()).isFalse();
  }

  @Test
  void legacyGlobalSecurityConfigToNamespaceSecurityPolicy_mapsDefaultOpenMode() {
    NamespaceSecurityPolicyDTO policy =
        TaktXClient.legacyGlobalSecurityConfigToNamespaceSecurityPolicy(
            GlobalConfigurationDTO.builder().build(), 7L, SecurityActivationState.VALIDATING);

    assertThat(policy.getMode()).isEqualTo(SecurityMode.COMMUNITY_OPEN);
    assertThat(policy.getActivationState()).isEqualTo(SecurityActivationState.VALIDATING);
    assertThat(policy.getRequiredSigning().isAnyRequired()).isFalse();
    assertThat(policy.getRequiredAuthorization().isAnyRequired()).isFalse();
  }

  @Test
  void legacyGlobalSecurityConfigToNamespaceSecurityPolicy_rejectsInvalidInputs() {
    assertThatThrownBy(
            () -> TaktXClient.legacyGlobalSecurityConfigToNamespaceSecurityPolicy(null, 1L))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("configuration must not be null");
    assertThatThrownBy(
            () ->
                TaktXClient.legacyGlobalSecurityConfigToNamespaceSecurityPolicy(
                    GlobalConfigurationDTO.builder().build(), 0L))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("desiredPolicyVersion must be > 0");
  }
}
