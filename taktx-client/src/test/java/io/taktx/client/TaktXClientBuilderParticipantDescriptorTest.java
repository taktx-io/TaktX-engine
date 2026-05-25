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

import io.taktx.dto.ParticipantCapability;
import io.taktx.dto.ParticipantKind;
import io.taktx.dto.SecurityParticipantDescriptor;
import java.util.LinkedHashSet;
import java.util.Properties;
import java.util.Set;
import org.junit.jupiter.api.Test;

class TaktXClientBuilderParticipantDescriptorTest {

  @Test
  void resolveParticipantDescriptor_infersDefaultRuntimeAndObserverCapabilities() {
    TaktXClient.TaktXClientBuilder builder = TaktXClient.newClientBuilder();

    SecurityParticipantDescriptor descriptor =
        builder.resolveParticipantDescriptor(baseProperties());

    assertThat(descriptor.participantId()).isEqualTo("tenant.default.client");
    assertThat(descriptor.kind()).isEqualTo(ParticipantKind.CLIENT);
    assertThat(descriptor.componentType()).isEqualTo("generic-client");
    assertThat(descriptor.capabilities())
        .containsExactly(
            ParticipantCapability.PROTECTED_RUNTIME_PARTICIPANT,
            ParticipantCapability.SECURITY_OBSERVER);
  }

  @Test
  void resolveParticipantDescriptor_addsAuthoritativePublisherWhenExplicitWriterIdentityExists() {
    Properties properties = baseProperties();
    properties.setProperty("taktx.signing.private-key", "private-key");
    properties.setProperty("taktx.signing.key-id", "publisher-key");

    TaktXClient.TaktXClientBuilder builder = TaktXClient.newClientBuilder();

    SecurityParticipantDescriptor descriptor = builder.resolveParticipantDescriptor(properties);

    assertThat(descriptor.capabilities())
        .contains(
            ParticipantCapability.PROTECTED_RUNTIME_PARTICIPANT,
            ParticipantCapability.SECURITY_OBSERVER,
            ParticipantCapability.AUTHORITATIVE_POLICY_PUBLISHER);
  }

  @Test
  void resolveParticipantDescriptor_preservesExplicitMixedCapabilityDescriptor() {
    Set<ParticipantCapability> capabilities = new LinkedHashSet<>();
    capabilities.add(ParticipantCapability.AUTHORITATIVE_POLICY_PUBLISHER);
    capabilities.add(ParticipantCapability.SECURITY_OBSERVER);
    capabilities.add(ParticipantCapability.PROTECTED_RUNTIME_PARTICIPANT);

    TaktXClient.TaktXClientBuilder builder =
        TaktXClient.newClientBuilder()
            .withParticipantDescriptor(
                new SecurityParticipantDescriptor(
                    "tenant.default.admin-console",
                    ParticipantKind.CLIENT,
                    capabilities,
                    " admin-console "));

    Properties properties = baseProperties();
    properties.setProperty("taktx.signing.private-key", "private-key");
    properties.setProperty("taktx.signing.key-id", "publisher-key");

    SecurityParticipantDescriptor descriptor = builder.resolveParticipantDescriptor(properties);

    assertThat(descriptor.participantId()).isEqualTo("tenant.default.admin-console");
    assertThat(descriptor.componentType()).isEqualTo("admin-console");
    assertThat(descriptor.capabilities()).containsExactlyElementsOf(capabilities);
  }

  @Test
  void resolveParticipantDescriptor_rejectsEngineKindDescriptors() {
    TaktXClient.TaktXClientBuilder builder =
        TaktXClient.newClientBuilder()
            .withParticipantDescriptor(
                new SecurityParticipantDescriptor(
                    "tenant.default.engine",
                    ParticipantKind.ENGINE,
                    Set.of(ParticipantCapability.SECURITY_OBSERVER),
                    "engine"));

    assertThatThrownBy(() -> builder.resolveParticipantDescriptor(baseProperties()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("kind must be CLIENT");
  }

  @Test
  void resolveParticipantDescriptor_rejectsEnforcerCapabilityForClients() {
    TaktXClient.TaktXClientBuilder builder =
        TaktXClient.newClientBuilder()
            .withParticipantDescriptor(
                new SecurityParticipantDescriptor(
                    "tenant.default.client",
                    ParticipantKind.CLIENT,
                    Set.of(ParticipantCapability.ENFORCER),
                    "client"));

    assertThatThrownBy(() -> builder.resolveParticipantDescriptor(baseProperties()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("must not declare ENFORCER");
  }

  @Test
  void resolveParticipantDescriptor_rejectsPublisherCapabilityWithoutExplicitWriterIdentity() {
    TaktXClient.TaktXClientBuilder builder =
        TaktXClient.newClientBuilder()
            .withParticipantDescriptor(
                new SecurityParticipantDescriptor(
                    "tenant.default.console",
                    ParticipantKind.CLIENT,
                    Set.of(
                        ParticipantCapability.AUTHORITATIVE_POLICY_PUBLISHER,
                        ParticipantCapability.SECURITY_OBSERVER),
                    "console"));

    assertThatThrownBy(() -> builder.resolveParticipantDescriptor(baseProperties()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("AUTHORITATIVE_POLICY_PUBLISHER requires an explicit authoritative signing identity");
  }

  private Properties baseProperties() {
    Properties properties = new Properties();
    properties.setProperty("bootstrap.servers", "localhost:9092");
    properties.setProperty("taktx.engine.tenant-id", "tenant");
    properties.setProperty("taktx.engine.namespace", "default");
    return properties;
  }
}

