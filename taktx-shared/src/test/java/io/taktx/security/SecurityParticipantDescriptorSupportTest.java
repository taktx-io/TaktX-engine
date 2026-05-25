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

import io.taktx.dto.ParticipantCapability;
import io.taktx.dto.ParticipantKind;
import io.taktx.dto.SecurityParticipantDescriptor;
import java.util.LinkedHashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class SecurityParticipantDescriptorSupportTest {

  @Test
  void normalize_trimsParticipantIdAndBlankOptionalComponentType() {
    SecurityParticipantDescriptor normalized =
        SecurityParticipantDescriptorSupport.normalize(
            new SecurityParticipantDescriptor(
                "  tenant.default.client  ",
                ParticipantKind.CLIENT,
                Set.of(ParticipantCapability.PROTECTED_RUNTIME_PARTICIPANT),
                "   "));

    assertThat(normalized.participantId()).isEqualTo("tenant.default.client");
    assertThat(normalized.componentType()).isNull();
  }

  @Test
  void requireValid_rejectsBlankParticipantIdAndEmptyCapabilities() {
    assertThatThrownBy(
            () ->
                SecurityParticipantDescriptorSupport.requireValid(
                    new SecurityParticipantDescriptor(" ", ParticipantKind.CLIENT, Set.of(), null)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("participantId must not be blank")
        .hasMessageContaining("capabilities must not be empty");
  }

  @Test
  void requireValid_rejectsNullCapabilities() {
    assertThatThrownBy(
            () ->
                SecurityParticipantDescriptorSupport.requireValid(
                    new SecurityParticipantDescriptor(
                        "tenant.default.client", ParticipantKind.CLIENT, null, "client")))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("capabilities must not be empty");
  }

  @Test
  void requireValid_rejectsNullCapabilityEntries() {
    Set<ParticipantCapability> capabilities = new LinkedHashSet<>();
    capabilities.add(ParticipantCapability.PROTECTED_RUNTIME_PARTICIPANT);
    capabilities.add(null);

    assertThatThrownBy(
            () ->
                SecurityParticipantDescriptorSupport.requireValid(
                    new SecurityParticipantDescriptor(
                        "tenant.default.client", ParticipantKind.CLIENT, capabilities, "client")))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("capabilities must not contain null values");
  }
}

