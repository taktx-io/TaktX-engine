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

import io.taktx.dto.ProcessDefinitionKey;
import java.nio.ByteBuffer;
import org.apache.kafka.common.errors.SerializationException;
import org.junit.jupiter.api.Test;

class ProcessDefinitionKeyDeserializerTest {

  @Test
  void deserialize_nullBytes_returnsNull() {
    try (ProcessDefinitionKeyDeserializer deserializer = new ProcessDefinitionKeyDeserializer()) {
      assertThat(deserializer.deserialize("process-definition", null)).isNull();
    }
  }

  @Test
  void fromBytes_roundTripsSerializedKey() {
    ProcessDefinitionKey expected = new ProcessDefinitionKey("order-process", 7);
    byte[] bytes = ProcessDefinitionKeySerializer.toBytes(expected);

    ProcessDefinitionKey actual = ProcessDefinitionKeyDeserializer.fromBytes(bytes);

    assertThat(actual).isEqualTo(expected);
  }

  @Test
  void fromBytes_rejectsUnexpectedPayloadLength() {
    byte[] truncated = new byte[] {0, 1, 'A', 0, 0, 0};
    byte[] extended =
        ByteBuffer.allocate(8).putShort((short) 1).put((byte) 'A').putInt(7).put((byte) 99).array();

    assertThatThrownBy(() -> ProcessDefinitionKeyDeserializer.fromBytes(truncated))
        .isInstanceOf(SerializationException.class)
        .hasMessageContaining("expected 7 bytes but got 6");

    assertThatThrownBy(() -> ProcessDefinitionKeyDeserializer.fromBytes(extended))
        .isInstanceOf(SerializationException.class)
        .hasMessageContaining("expected 7 bytes but got 8");
  }

  @Test
  void serializedLength_rejectsTooShortPayload() {
    assertThatThrownBy(
            () -> ProcessDefinitionKeyDeserializer.serializedLength(new byte[] {0x00}, 0))
        .isInstanceOf(SerializationException.class)
        .hasMessageContaining("expected at least 6 bytes");
  }

  @Test
  void serializedLength_withOffsetReturnsEndIndex() {
    ProcessDefinitionKey key = new ProcessDefinitionKey("demo", 3);
    byte[] serialized = ProcessDefinitionKeySerializer.toBytes(key);
    byte[] prefixed =
        ByteBuffer.allocate(serialized.length + 3)
            .put(new byte[] {9, 8, 7})
            .put(serialized)
            .array();

    int endIndex = ProcessDefinitionKeyDeserializer.serializedLength(prefixed, 3);

    assertThat(endIndex).isEqualTo(prefixed.length);
  }
}
