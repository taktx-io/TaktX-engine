/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.client.serdes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.taktx.dto.DmnDefinitionKey;
import io.taktx.serdes.DmnDefinitionKeyProtoMapper;
import org.junit.jupiter.api.Test;

class DmnDefinitionKeyDeserializerTest {

  @Test
  void deserializesCurrentProtoKeyFormat() {
    DmnDefinitionKey original = new DmnDefinitionKey("decision-table", 4);
    byte[] bytes = DmnDefinitionKeyProtoMapper.toProto(original).toByteArray();

    DmnDefinitionKey decoded;
    try (DmnDefinitionKeyDeserializer deserializer = new DmnDefinitionKeyDeserializer()) {
      decoded = deserializer.deserialize("topic", bytes);
    }

    assertThat(decoded).isEqualTo(original);
  }

  @Test
  void rejectsInvalidBytes() {
    try (DmnDefinitionKeyDeserializer deserializer = new DmnDefinitionKeyDeserializer()) {
      assertThatThrownBy(() -> deserializer.deserialize("topic", new byte[] {0x01, 0x02, 0x03}))
          .isInstanceOf(RuntimeException.class);
    }
  }
}
