/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class TaktLongListSerdeTest {

  @Test
  void serializeDeserialize_roundTrip_nonEmptyList() {
    List<Long> original = List.of(1L, 42L, Long.MAX_VALUE, Long.MIN_VALUE);
    TaktLongListSerializer serializer = new TaktLongListSerializer();
    TaktLongListDeserializer deserializer = new TaktLongListDeserializer();

    byte[] bytes = serializer.serialize("topic", original);
    List<Long> result = deserializer.deserialize("topic", bytes);

    assertThat(result).isEqualTo(original);
  }

  @Test
  void serializeDeserialize_roundTrip_emptyList() {
    List<Long> original = List.of();
    TaktLongListSerializer serializer = new TaktLongListSerializer();
    TaktLongListDeserializer deserializer = new TaktLongListDeserializer();

    byte[] bytes = serializer.serialize("topic", original);
    List<Long> result = deserializer.deserialize("topic", bytes);

    assertThat(result).isEmpty();
  }

  @Test
  void serialize_encodesCount_asFirstFourBytes() {
    List<Long> list = List.of(10L, 20L, 30L);
    TaktLongListSerializer serializer = new TaktLongListSerializer();
    byte[] bytes = serializer.serialize("topic", list);

    // 4 bytes for the count + 8 bytes per element
    assertThat(bytes).hasSize(4 + 8 * 3);
  }

  @Test
  void serializeDeserialize_singleElement() {
    List<Long> original = List.of(99L);
    TaktLongListSerializer serializer = new TaktLongListSerializer();
    TaktLongListDeserializer deserializer = new TaktLongListDeserializer();

    assertThat(deserializer.deserialize("t", serializer.serialize("t", original)))
        .containsExactly(99L);
  }
}
