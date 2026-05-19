/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.client.serdes;

import static org.assertj.core.api.Assertions.assertThat;

import io.taktx.dto.DmnDefinitionKey;
import io.taktx.serdes.DmnDefinitionKeyProtoMapper;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class DmnDefinitionKeyJsonDeserializerTest {

  @Test
  void deserializesCurrentProtoKeyFormat() {
    DmnDefinitionKey original = new DmnDefinitionKey("decision-table", 4);
    byte[] bytes = DmnDefinitionKeyProtoMapper.toProto(original).toByteArray();

    DmnDefinitionKey decoded;
    try (DmnDefinitionKeyJsonDeserializer deserializer = new DmnDefinitionKeyJsonDeserializer()) {
      decoded = deserializer.deserialize("topic", bytes);
    }

    assertThat(decoded).isEqualTo(original);
  }

  @Test
  void deserializesLegacyCborKeyFormat() {
    DmnDefinitionKey original = new DmnDefinitionKey("legacy-decision", 2);
    byte[] bytes = legacyDmnDefinitionKeyBytes();

    DmnDefinitionKey decoded;
    try (DmnDefinitionKeyJsonDeserializer deserializer = new DmnDefinitionKeyJsonDeserializer()) {
      decoded = deserializer.deserialize("topic", bytes);
    }

    assertThat(decoded).isEqualTo(original);
  }

  private static byte[] legacyDmnDefinitionKeyBytes() {
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    output.write(0x82);
    writeText(output, "legacy-decision");
    writeInteger(output, 2);
    return output.toByteArray();
  }

  private static void writeText(ByteArrayOutputStream output, String value) {
    byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
    writeLength(output, 3, bytes.length);
    output.writeBytes(bytes);
  }

  private static void writeInteger(ByteArrayOutputStream output, int value) {
    if (value >= 0) {
      writeLength(output, 0, value);
      return;
    }
    writeLength(output, 1, -1L - value);
  }

  private static void writeLength(ByteArrayOutputStream output, int majorType, long value) {
    if (value < 24) {
      output.write((majorType << 5) | (int) value);
    } else if (value < 256) {
      output.write((majorType << 5) | 24);
      output.write((int) value);
    } else {
      throw new IllegalArgumentException("Test fixture only supports values smaller than 256");
    }
  }
}
