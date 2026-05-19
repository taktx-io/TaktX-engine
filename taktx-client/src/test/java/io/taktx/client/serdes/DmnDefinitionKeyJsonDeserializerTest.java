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
import io.taktx.jackson.TaktxObjectMappers;
import io.taktx.serdes.DmnDefinitionKeyProtoMapper;
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
  void deserializesLegacyCborKeyFormat() throws Exception {
    DmnDefinitionKey original = new DmnDefinitionKey("legacy-decision", 2);
    byte[] bytes = TaktxObjectMappers.cbor().writeValueAsBytes(original);

    DmnDefinitionKey decoded;
    try (DmnDefinitionKeyJsonDeserializer deserializer = new DmnDefinitionKeyJsonDeserializer()) {
      decoded = deserializer.deserialize("topic", bytes);
    }

    assertThat(decoded).isEqualTo(original);
  }
}
