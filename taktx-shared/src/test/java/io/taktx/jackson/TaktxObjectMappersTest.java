/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.jackson;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.cbor.CBORFactory;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.taktx.dto.ConfigurationEventDTO;
import io.taktx.dto.GlobalConfigurationDTO;
import io.taktx.dto.KeyRole;
import io.taktx.dto.ReplayProtectionMode;
import io.taktx.dto.SigningKeyDTO;
import io.taktx.dto.SigningKeyDTO.KeyStatus;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class TaktxObjectMappersTest {

  @Test
  void cborMapper_readsLegacyNumericInstant_forSigningKeyRecords() throws Exception {
    Instant createdAt = Instant.parse("2026-05-18T10:03:36.442509Z");
    SigningKeyDTO signingKey =
        SigningKeyDTO.builder()
            .keyId("revoked-worker-key-1")
            .publicKeyBase64("test-public-key")
            .algorithm("Ed25519")
            .createdAt(createdAt)
            .status(KeyStatus.REVOKED)
            .owner("revoked-worker")
            .role(KeyRole.CLIENT)
            .build();

    ObjectMapper legacyCborMapper = new ObjectMapper(new CBORFactory()).registerModule(new JavaTimeModule());
    byte[] payload = legacyCborMapper.writeValueAsBytes(signingKey);

    SigningKeyDTO decoded = TaktxObjectMappers.cbor().readValue(payload, SigningKeyDTO.class);

    assertThat(decoded).isEqualTo(signingKey);
    assertThat(decoded.getCreatedAt()).isEqualTo(createdAt);
  }

  @Test
  void jsonMapper_readsLegacyNumericInstant_forConfigurationEvents() throws Exception {
    Instant timestamp = Instant.parse("2026-05-18T11:22:33.123456789Z");
    ConfigurationEventDTO event =
        ConfigurationEventDTO.builder()
            .eventType(ConfigurationEventDTO.ConfigurationEventType.CONFIGURATION_UPDATE)
            .configuration(
                GlobalConfigurationDTO.builder()
                    .signingEnabled(true)
                    .engineRequiresAuthorization(true)
                    .trustedKeyIds(java.util.List.of("engine-key-1"))
                    .replayProtectionMode(ReplayProtectionMode.STRICT)
                    .replayProtectionRetentionMs(1234L)
                    .build())
            .timestamp(timestamp)
            .publishedByInstance("engine-1")
            .build();

    ObjectMapper legacyJsonMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    byte[] payload = legacyJsonMapper.writeValueAsBytes(event);

    ConfigurationEventDTO decoded =
        TaktxObjectMappers.json().readValue(payload, ConfigurationEventDTO.class);

    assertThat(decoded).isEqualTo(event);
    assertThat(decoded.getTimestamp()).isEqualTo(timestamp);
  }
}


