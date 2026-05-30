/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.client;

import static org.assertj.core.api.Assertions.assertThat;

import io.taktx.dto.NamespaceSecurityPolicyDTO;
import io.taktx.dto.ParticipantCapability;
import io.taktx.dto.ParticipantKind;
import io.taktx.dto.SecurityParticipantDescriptor;
import io.taktx.dto.VariablesDTO;
import io.taktx.security.SigningKeyGenerator;
import java.lang.reflect.Method;
import java.util.LinkedHashSet;
import java.util.Properties;
import java.util.Set;
import org.junit.jupiter.api.Test;

class TaktXClientFacetTest {

  @Test
  void facetEntryPoints_areCachedAndAvailableFromRootClient() {
    TaktXClient client = buildClient(runtimeObserverDescriptor());
    try {
      assertThat(client.security()).isSameAs(client.security());
      assertThat(client.observability()).isSameAs(client.observability());
      assertThat(client.runtime()).isSameAs(client.runtime());
      assertThat(client.workers()).isSameAs(client.workers());
      assertThat(client.dlq()).isSameAs(client.dlq());
    } finally {
      client.stop();
    }
  }

  @Test
  void mixedCapabilityParticipants_canAccessSecurityRuntimeAndObservabilityFacets() {
    Set<ParticipantCapability> capabilities = new LinkedHashSet<>();
    capabilities.add(ParticipantCapability.AUTHORITATIVE_POLICY_PUBLISHER);
    capabilities.add(ParticipantCapability.SECURITY_OBSERVER);
    capabilities.add(ParticipantCapability.PROTECTED_RUNTIME_PARTICIPANT);

    SecurityParticipantDescriptor descriptor =
        new SecurityParticipantDescriptor(
            "tenant.default.admin-console", ParticipantKind.CLIENT, capabilities, "admin-console");

    TaktXClient client = buildClient(descriptor, publisherProperties());
    try {
      SecurityClient securityFacet = client.security();
      SecurityObservabilityClient observabilityFacet = client.observability();
      RuntimeClient runtimeFacet = client.runtime();

      assertThat(client.getParticipantDescriptor().capabilities())
          .containsExactlyElementsOf(capabilities);
      assertThat(securityFacet).isNotNull();
      assertThat(observabilityFacet).isNotNull();
      assertThat(runtimeFacet).isNotNull();
    } finally {
      client.stop();
    }
  }

  @Test
  void policyMutationAndObservationAreExposedThroughPublicFacetApis() throws Exception {
    Method securityEntryPoint = TaktXClient.class.getMethod("security");
    Method observabilityEntryPoint = TaktXClient.class.getMethod("observability");
    Method runtimeEntryPoint = TaktXClient.class.getMethod("runtime");
    Method workersEntryPoint = TaktXClient.class.getMethod("workers");
    Method dlqEntryPoint = TaktXClient.class.getMethod("dlq");

    assertThat(securityEntryPoint.getReturnType()).isEqualTo(SecurityClient.class);
    assertThat(observabilityEntryPoint.getReturnType())
        .isEqualTo(SecurityObservabilityClient.class);
    assertThat(runtimeEntryPoint.getReturnType()).isEqualTo(RuntimeClient.class);
    assertThat(workersEntryPoint.getReturnType()).isEqualTo(WorkersClient.class);
    assertThat(dlqEntryPoint.getReturnType()).isEqualTo(DlqClient.class);

    assertThat(
            SecurityClient.class.getMethod(
                "publishNamespaceSecurityPolicy", NamespaceSecurityPolicyDTO.class))
        .isNotNull();
    assertThat(SecurityClient.class.getMethod("clearNamespaceSecurityPolicy")).isNotNull();
    assertThat(SecurityClient.class.getMethod("authoritativePolicyMutationAvailability")).isNotNull();
    assertThat(RuntimeClient.class.getMethod("startProcess", String.class, VariablesDTO.class))
        .isNotNull();
    assertThat(
            WorkersClient.class.getMethod(
                "registerExternalTaskConsumer", ExternalTaskTriggerConsumer.class, String.class))
        .isNotNull();
    assertThat(
            DlqClient.class.getMethod("submitReplayCommand", io.taktx.dto.DlqReplayCommand.class))
        .isNotNull();
  }

  private TaktXClient buildClient(SecurityParticipantDescriptor descriptor) {
    return buildClient(descriptor, baseProperties());
  }

  private TaktXClient buildClient(SecurityParticipantDescriptor descriptor, Properties properties) {
    return TaktXClient.newClientBuilder()
        .withProperties(properties)
        .withParticipantDescriptor(descriptor)
        .build();
  }

  private Properties baseProperties() {
    Properties properties = new Properties();
    properties.setProperty("bootstrap.servers", "localhost:9092");
    properties.setProperty("taktx.engine.tenant-id", "tenant");
    properties.setProperty("taktx.engine.namespace", "default");
    return properties;
  }

  private Properties publisherProperties() {
    java.security.KeyPair keyPair = SigningKeyGenerator.generate();
    Properties properties = baseProperties();
    properties.setProperty(
        "taktx.signing.private-key", SigningKeyGenerator.encodePrivateKey(keyPair.getPrivate()));
    properties.setProperty(
        "taktx.signing.public-key", SigningKeyGenerator.encodePublicKey(keyPair.getPublic()));
    properties.setProperty("taktx.signing.key-id", "publisher-key");
    return properties;
  }

  private SecurityParticipantDescriptor runtimeObserverDescriptor() {
    return new SecurityParticipantDescriptor(
        "tenant.default.client",
        ParticipantKind.CLIENT,
        Set.of(
            ParticipantCapability.PROTECTED_RUNTIME_PARTICIPANT,
            ParticipantCapability.SECURITY_OBSERVER),
        "generic-client");
  }
}
