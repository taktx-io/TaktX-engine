/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.security;

import static org.assertj.core.api.Assertions.assertThat;

import io.taktx.dto.ParticipantCapability;
import io.taktx.dto.ParticipantKind;
import io.taktx.dto.ParticipantStatusDTO;
import io.taktx.dto.SecurityParticipantDescriptor;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.RecordComponent;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class SharedParticipantIdentityContractTest {

  @Test
  void sharedContract_exposesKindCapabilitiesAndComponentTypeInsteadOfLegacyRoleField()
      throws IOException {
    assertThat(
            Stream.of(SecurityParticipantDescriptor.class.getRecordComponents())
                .map(RecordComponent::getName))
        .containsExactly("participantId", "kind", "capabilities", "componentType")
        .doesNotContain("role", "participantRole");

    assertThat(Arrays.stream(ParticipantStatusDTO.class.getDeclaredFields()).map(Field::getName))
        .contains("participantKind", "capabilities", "componentType")
        .doesNotContain("role", "participantRole");

    String protoSchema =
        Files.readString(
            Path.of("src/main/proto/io/taktx/proto/security_observability.proto"),
            StandardCharsets.UTF_8);

    assertThat(protoSchema).contains("ParticipantKindMessage participant_kind = 3;");
    assertThat(protoSchema).contains("string component_type = 14;");
    assertThat(protoSchema).contains("repeated ParticipantCapabilityMessage capabilities = 15;");
    assertThat(protoSchema).doesNotContain(" role = ");
    assertThat(protoSchema).doesNotContain("participant_role");
  }

  @Test
  void sharedParticipantEnums_doNotReintroduceLegacyProductSpecificRoleValues() {
    assertThat(ParticipantKind.values()).extracting(Enum::name).containsExactly("ENGINE", "CLIENT");

    assertThat(ParticipantCapability.values())
        .extracting(Enum::name)
        .containsExactly(
            "ENFORCER",
            "PROTECTED_RUNTIME_PARTICIPANT",
            "SECURITY_OBSERVER")
        .doesNotContain("AUTHORITATIVE_POLICY_PUBLISHER", "ENGINE", "INGESTER", "CONSOLE", "CLIENT");
  }
}
