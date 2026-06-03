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

import io.taktx.dto.Constants;
import io.taktx.dto.GlobalConfigurationDTO;
import io.taktx.dto.NamespaceSecurityPolicyDTO;
import io.taktx.dto.SecurityActivationState;
import io.taktx.dto.SecurityMode;
import io.taktx.dto.SecurityPostureIssueCodes;
import io.taktx.security.AuthoritativeControlPlaneSecurityProperty;
import io.taktx.security.Ed25519Service;
import io.taktx.security.NamespaceSecurityPolicyActivationAuthority;
import io.taktx.security.SigningIdentity;
import io.taktx.security.SigningKeyGenerator;
import io.taktx.serdes.NamespaceSecurityPolicyProtoMapper;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Properties;
import org.junit.jupiter.api.Test;

class TaktXClientNamespaceSecurityPolicyTest {

  @Test
  void normalizeNamespaceSecurityPolicy_fillsCanonicalHashWhenMissing() {
    NamespaceSecurityPolicyDTO normalized =
        TaktXClient.normalizeNamespaceSecurityPolicy(
            NamespaceSecurityPolicyDTO.builder()
                .mode(SecurityMode.ANCHORED)
                .policyVersion(42L)
                .build());

    assertThat(normalized.getPolicyVersion()).isEqualTo(42L);
    assertThat(normalized.getPolicyHash()).isNotBlank();
  }

  @Test
  void canonicalNamespaceSecurityPolicyHash_dependsOnModeAndPolicyVersionOnly() {
    NamespaceSecurityPolicyDTO baseline =
        NamespaceSecurityPolicyDTO.builder()
            .mode(SecurityMode.ANCHORED)
            .policyVersion(1L)
            .policyHash("hash-a")
            .build();

    NamespaceSecurityPolicyDTO sameEffectivePolicy =
        baseline.toBuilder().policyHash("hash-b").build();

    assertThat(TaktXClient.canonicalNamespaceSecurityPolicyHash(baseline))
        .isEqualTo(TaktXClient.canonicalNamespaceSecurityPolicyHash(sameEffectivePolicy));
  }

  @Test
  void canonicalNamespaceSecurityPolicyHash_changesWhenModeChanges() {
    NamespaceSecurityPolicyDTO openPolicy =
        NamespaceSecurityPolicyDTO.builder().mode(SecurityMode.OPEN).policyVersion(1L).build();
    NamespaceSecurityPolicyDTO anchoredPolicy =
        NamespaceSecurityPolicyDTO.builder().mode(SecurityMode.ANCHORED).policyVersion(1L).build();

    assertThat(TaktXClient.canonicalNamespaceSecurityPolicyHash(openPolicy))
        .isNotEqualTo(TaktXClient.canonicalNamespaceSecurityPolicyHash(anchoredPolicy));
  }

  @Test
  void validateNamespaceSecurityPolicy_rejectsMissingMode() {
    NamespaceSecurityPolicyDTO policy =
        NamespaceSecurityPolicyDTO.builder().policyVersion(1L).build();

    assertThatThrownBy(() -> TaktXClient.validateNamespaceSecurityPolicy(policy))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("mode must not be null");
  }

  @Test
  void validateNamespaceSecurityPolicy_returnsNormalizedPolicyForValidInput() {
    NamespaceSecurityPolicyDTO validated =
        TaktXClient.validateNamespaceSecurityPolicy(
            NamespaceSecurityPolicyDTO.builder()
                .mode(SecurityMode.ANCHORED)
                .policyVersion(99L)
                .build());

    assertThat(validated.getPolicyVersion()).isEqualTo(99L);
    assertThat(validated.getPolicyHash()).isNotBlank();
  }

  @Test
  void buildNamespaceSecurityPolicyRecord_usesPolicyKeyAndSerializesValidatedPolicy()
      throws Exception {
    NamespaceSecurityPolicyDTO input =
        NamespaceSecurityPolicyDTO.builder().mode(SecurityMode.ANCHORED).policyVersion(42L).build();

    var producerRecord =
        TaktXClient.buildNamespaceSecurityPolicyRecord(
            "tenant.bank.payments.taktx-security-policy", input);

    assertThat(producerRecord.topic()).isEqualTo("tenant.bank.payments.taktx-security-policy");
    assertThat(producerRecord.key()).isEqualTo(TaktXClient.NAMESPACE_SECURITY_POLICY_RECORD_KEY);
    NamespaceSecurityPolicyDTO serialized =
        NamespaceSecurityPolicyProtoMapper.toDto(
            io.taktx.proto.NamespaceSecurityPolicyMessage.parseFrom(producerRecord.value()));
    assertThat(serialized.getPolicyVersion()).isEqualTo(42L);
    assertThat(serialized.getPolicyHash()).isNotBlank();
    assertThat(serialized.getMode()).isEqualTo(SecurityMode.ANCHORED);
  }

  @Test
  void buildNamespaceSecurityPolicyRecord_rejectsBlankTopic() {
    NamespaceSecurityPolicyDTO input =
        NamespaceSecurityPolicyDTO.builder().mode(SecurityMode.OPEN).policyVersion(42L).build();

    assertThatThrownBy(() -> TaktXClient.buildNamespaceSecurityPolicyRecord(" ", input))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("topic must not be blank");
  }

  @Test
  void buildNamespaceSecurityPolicyTombstoneRecord_usesPolicyKeyAndNullValue() {
    var producerRecord =
        TaktXClient.buildNamespaceSecurityPolicyTombstoneRecord(
            "tenant.bank.payments.taktx-security-policy");

    assertThat(producerRecord.topic()).isEqualTo("tenant.bank.payments.taktx-security-policy");
    assertThat(producerRecord.key()).isEqualTo(TaktXClient.NAMESPACE_SECURITY_POLICY_RECORD_KEY);
    assertThat(producerRecord.value()).isNull();
  }

  @Test
  void buildNamespaceSecurityPolicyTombstoneRecord_rejectsBlankTopic() {
    assertThatThrownBy(() -> TaktXClient.buildNamespaceSecurityPolicyTombstoneRecord(" "))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("topic must not be blank");
  }

  @Test
  void buildNamespaceSecurityPolicyRecord_withSigningIdentity_attachesVerifiableSignatureHeader() {
    java.security.KeyPair keyPair = SigningKeyGenerator.generate();
    String privateKeyBase64 = SigningKeyGenerator.encodePrivateKey(keyPair.getPrivate());
    String publicKeyBase64 = SigningKeyGenerator.encodePublicKey(keyPair.getPublic());
    SigningIdentity signingIdentity =
        SigningIdentity.ed25519("platform-policy-key", privateKeyBase64, publicKeyBase64);

    NamespaceSecurityPolicyDTO input =
        NamespaceSecurityPolicyDTO.builder().mode(SecurityMode.ANCHORED).policyVersion(42L).build();

    var producerRecord =
        TaktXClient.buildNamespaceSecurityPolicyRecord(
            "tenant.bank.payments.taktx-security-policy", input, signingIdentity);

    assertThat(producerRecord.headers().lastHeader(Constants.HEADER_ENGINE_SIGNATURE)).isNotNull();
    String headerValue =
        new String(
            producerRecord.headers().lastHeader(Constants.HEADER_ENGINE_SIGNATURE).value(),
            StandardCharsets.UTF_8);
    assertThat(headerValue).startsWith("platform-policy-key.");
    String base64Signature = headerValue.substring(headerValue.indexOf('.') + 1);
    assertThat(
            Ed25519Service.verify(
                producerRecord.value(),
                Base64.getDecoder().decode(base64Signature),
                publicKeyBase64))
        .isTrue();
  }

  @Test
  void publishNamespaceSecurityPolicy_requiresExplicitTrustedWriterIdentity() {
    NamespaceSecurityPolicyDTO input =
        NamespaceSecurityPolicyDTO.builder().mode(SecurityMode.OPEN).policyVersion(42L).build();

    Properties properties = new Properties();
    properties.setProperty("bootstrap.servers", "localhost:9092");
    properties.setProperty("taktx.engine.tenant-id", "tenant");
    properties.setProperty("taktx.engine.namespace", "bank.payments");

    assertThatThrownBy(() -> TaktXClient.publishNamespaceSecurityPolicy(properties, input))
        .isInstanceOf(SecurityControlPlaneMutationException.class)
        .extracting("code")
        .isEqualTo(SecurityPostureIssueCodes.AUTHORITATIVE_WRITER_UNCONFIGURED);
    assertThatThrownBy(() -> TaktXClient.publishNamespaceSecurityPolicy(properties, input))
        .isInstanceOf(SecurityControlPlaneMutationException.class)
        .hasMessageContaining("explicit signing identity")
        .hasMessageContaining("authoritative writer");
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
  void
      namespaceSecurityPolicyWriterSecurityProperties_forAnchoredPolicy_addsIntegrityRequirement() {
    NamespaceSecurityPolicyDTO policy =
        NamespaceSecurityPolicyDTO.builder().mode(SecurityMode.ANCHORED).policyVersion(42L).build();

    assertThat(TaktXClient.namespaceSecurityPolicyWriterSecurityProperties(policy))
        .contains(
            AuthoritativeControlPlaneSecurityProperty
                .INTEGRITY_PROTECTION_REQUIRED_IN_SECURED_MODES,
            AuthoritativeControlPlaneSecurityProperty.BROKER_AUTHORIZATION_REQUIRED,
            AuthoritativeControlPlaneSecurityProperty.TRUSTED_WRITER_PATH_ONLY,
            AuthoritativeControlPlaneSecurityProperty.FIXED_RECORD_KEY_REQUIRED);
  }

  @Test
  void namespaceSecurityPolicyActivationAuthority_exposesPlatformServiceAsSoleAuthority() {
    assertThat(TaktXClient.namespaceSecurityPolicyActivationAuthority())
        .isEqualTo(NamespaceSecurityPolicyActivationAuthority.PLATFORM_SERVICE);
  }

  @Test
  void legacyGlobalSecurityConfigToNamespaceSecurityPolicy_mapsSecurityFlagsToAnchoredMode() {
    NamespaceSecurityPolicyDTO policy =
        TaktXClient.legacyGlobalSecurityConfigToNamespaceSecurityPolicy(
            GlobalConfigurationDTO.builder()
                .signingEnabled(true)
                .engineRequiresAuthorization(true)
                .engineRequiresExternalTaskAuthorization(true)
                .engineRequiresUserTaskAuthorization(false)
                .build(),
            42L);

    assertThat(policy.getMode()).isEqualTo(SecurityMode.ANCHORED);
    assertThat(policy.getPolicyVersion()).isEqualTo(42L);
    assertThat(policy.getPolicyHash()).isNotBlank();
  }

  @Test
  void legacyGlobalSecurityConfigToNamespaceSecurityPolicy_mapsDefaultOpenMode() {
    NamespaceSecurityPolicyDTO policy =
        TaktXClient.legacyGlobalSecurityConfigToNamespaceSecurityPolicy(
            GlobalConfigurationDTO.builder().build(), 7L, SecurityActivationState.VALIDATING);

    assertThat(policy.getMode()).isEqualTo(SecurityMode.OPEN);
    assertThat(policy.getPolicyVersion()).isEqualTo(7L);
    assertThat(policy.getPolicyHash()).isNotBlank();
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
