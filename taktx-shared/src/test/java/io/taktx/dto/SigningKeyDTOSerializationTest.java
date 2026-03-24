/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks All rights reserved.
 * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 * Free use is permitted with up to 3 Kafka partitions per topic. See LICENSE file for details.
 * For commercial use or more partitions and features, contact [https://www.taktx.io/contact].
 */
package io.taktx.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.cbor.CBORFactory;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.Instant;
import org.junit.jupiter.api.Test;

/**
 * Validates CBOR wire-format migration safety for {@link SigningKeyDTO}.
 *
 * <p>Before the {@code role} field was added, the CBOR array had 6 elements. The new class has 7
 * fields. Jackson with {@code @JsonFormat(shape = ARRAY)} must be able to deserialize the old
 * (shorter) array without errors.
 */
class SigningKeyDTOSerializationTest {

  private static final ObjectMapper CBOR =
      new ObjectMapper(new CBORFactory()).registerModule(new JavaTimeModule());

  @Test
  void deserialize_newFormat_roundtrips() throws Exception {
    SigningKeyDTO original =
        SigningKeyDTO.builder()
            .keyId("engine-001")
            .publicKeyBase64("dGVzdA==")
            .algorithm("Ed25519")
            .createdAt(Instant.parse("2026-01-01T00:00:00Z"))
            .status(SigningKeyDTO.KeyStatus.ACTIVE)
            .owner("engine")
            .role(KeyRole.ENGINE)
            .build();

    byte[] bytes = CBOR.writeValueAsBytes(original);
    SigningKeyDTO deserialized = CBOR.readValue(bytes, SigningKeyDTO.class);

    assertThat(deserialized.getKeyId()).isEqualTo("engine-001");
    assertThat(deserialized.getRole()).isEqualTo(KeyRole.ENGINE);
    assertThat(deserialized.effectiveRole()).isEqualTo(KeyRole.ENGINE);
  }

  /**
   * Simulates deserializing a {@link SigningKeyDTO} that was serialized BEFORE the {@code role}
   * field was added (6-element CBOR array). The {@code role} field should deserialize as {@code
   * null}, and {@link SigningKeyDTO#effectiveRole()} must return {@code CLIENT}.
   */
  @Test
  void deserialize_oldFormatWithoutRole_yieldsNullRole() throws Exception {
    // Serialize a "legacy" DTO with role=null via the builder.
    // Using builder with .role(null) overrides the @Builder.Default so the serialized
    // CBOR will contain a null in the role slot — simulating a pre-role record.
    SigningKeyDTO legacyKey =
        SigningKeyDTO.builder()
            .keyId("worker-legacy-001")
            .publicKeyBase64("dGVzdA==")
            .algorithm("Ed25519")
            .createdAt(Instant.parse("2025-06-01T00:00:00Z"))
            .status(SigningKeyDTO.KeyStatus.ACTIVE)
            .owner("worker-legacy")
            .role(null) // simulates pre-role serialization
            .build();

    // Serialize with the current mapper (7 fields, role=null)
    byte[] bytes = CBOR.writeValueAsBytes(legacyKey);

    // Deserialize — role field should come back as null (CBOR null entry in array)
    SigningKeyDTO deserialized = CBOR.readValue(bytes, SigningKeyDTO.class);

    assertThat(deserialized.getKeyId()).isEqualTo("worker-legacy-001");
    assertThat(deserialized.getRole()).isNull();
    // effectiveRole() must treat null as CLIENT
    assertThat(deserialized.effectiveRole()).isEqualTo(KeyRole.CLIENT);
    assertThat(deserialized.getStatus()).isEqualTo(SigningKeyDTO.KeyStatus.ACTIVE);
  }

  @Test
  void effectiveRole_defaultsToClientForBuilderWithoutRole() {
    // When role is not set explicitly but @Builder.Default applies, it should be CLIENT
    SigningKeyDTO key =
        SigningKeyDTO.builder()
            .keyId("k1")
            .publicKeyBase64("x")
            .algorithm("Ed25519")
            .owner("worker")
            .build();

    assertThat(key.getRole()).isEqualTo(KeyRole.CLIENT);
    assertThat(key.effectiveRole()).isEqualTo(KeyRole.CLIENT);
  }
}
