/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class TaktUUIDSerdeTest {

  @Test
  void serializeDeserialize_roundTrip_viaKafka() {
    UUID original = UUID.randomUUID();
    TaktUUIDSerializer serializer = new TaktUUIDSerializer();
    TaktUUIDDeserializer deserializer = new TaktUUIDDeserializer();

    byte[] bytes = serializer.serialize("topic", original);
    UUID result = deserializer.deserialize("topic", bytes);

    assertThat(result).isEqualTo(original);
  }

  @Test
  void serde_wraps_serializerAndDeserializer() {
    UUID original = UUID.randomUUID();
    TaktUUIDSerde serde = new TaktUUIDSerde();

    byte[] bytes = serde.serializer().serialize("topic", original);
    UUID result = serde.deserializer().deserialize("topic", bytes);

    assertThat(result).isEqualTo(original);
  }

  @Test
  void serialize_producesExactly16Bytes() {
    TaktUUIDSerializer serializer = new TaktUUIDSerializer();
    byte[] bytes = serializer.serialize("topic", UUID.randomUUID());
    assertThat(bytes).hasSize(16);
  }

  @Test
  void roundTrip_preservesMostAndLeastSignificantBits() {
    UUID uuid = new UUID(Long.MAX_VALUE, Long.MIN_VALUE);
    TaktUUIDSerializer serializer = new TaktUUIDSerializer();
    TaktUUIDDeserializer deserializer = new TaktUUIDDeserializer();

    UUID result = deserializer.deserialize("t", serializer.serialize("t", uuid));

    assertThat(result.getMostSignificantBits()).isEqualTo(Long.MAX_VALUE);
    assertThat(result.getLeastSignificantBits()).isEqualTo(Long.MIN_VALUE);
  }
}
