/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.ByteBuffer;
import java.util.UUID;
import org.apache.kafka.common.errors.SerializationException;
import org.junit.jupiter.api.Test;

class TaktUUIDDeserializerTest {

  private final TaktUUIDDeserializer deserializer = new TaktUUIDDeserializer();

  // ── happy path ───────────────────────────────────────────────────────────

  @Test
  void deserialize_validUuidBytes_returnsCorrectUuid() {
    UUID expected = UUID.fromString("a1b2c3d4-e5f6-7890-abcd-ef1234567890");
    byte[] bytes = toBytes(expected);

    UUID result = deserializer.deserialize("any-topic", bytes);

    assertThat(result).isEqualTo(expected);
  }

  @Test
  void deserialize_knownBytes_roundTrips() {
    UUID original = UUID.randomUUID();
    byte[] bytes = toBytes(original);

    UUID result = deserializer.deserialize("topic", bytes);

    assertThat(result).isEqualTo(original);
  }

  // ── null key (engine crash scenario) ─────────────────────────────────────

  @Test
  void deserialize_nullBytes_throwsSerializationException() {
    assertThatThrownBy(() -> deserializer.deserialize("acme.default.process-instance", null))
        .isInstanceOf(SerializationException.class)
        .hasMessageContaining("null")
        .hasMessageContaining("16");
  }

  // ── wrong length ──────────────────────────────────────────────────────────

  @Test
  void deserialize_emptyBytes_throwsSerializationException() {
    assertThatThrownBy(() -> deserializer.deserialize("topic", new byte[0]))
        .isInstanceOf(SerializationException.class)
        .hasMessageContaining("expected 16 bytes but got 0");
  }

  @Test
  void deserialize_shortBytes_throwsSerializationException() {
    assertThatThrownBy(() -> deserializer.deserialize("topic", new byte[8]))
        .isInstanceOf(SerializationException.class)
        .hasMessageContaining("expected 16 bytes but got 8");
  }

  @Test
  void deserialize_tooManyBytes_throwsSerializationException() {
    assertThatThrownBy(() -> deserializer.deserialize("topic", new byte[17]))
        .isInstanceOf(SerializationException.class)
        .hasMessageContaining("expected 16 bytes but got 17");
  }

  @Test
  void deserialize_garbageString_throwsSerializationException() {
    byte[] garbage = "not-valid-cbor".getBytes();
    assertThatThrownBy(() -> deserializer.deserialize("acme.default.process-instance", garbage))
        .isInstanceOf(SerializationException.class)
        .hasMessageContaining("expected 16 bytes but got");
  }

  // ── helpers ───────────────────────────────────────────────────────────────

  private static byte[] toBytes(UUID uuid) {
    ByteBuffer buffer = ByteBuffer.allocate(16);
    buffer.putLong(uuid.getMostSignificantBits());
    buffer.putLong(uuid.getLeastSignificantBits());
    return buffer.array();
  }
}
