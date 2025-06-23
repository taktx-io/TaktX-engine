/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks All rights reserved.
 * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 * Free use is permitted with up to 3 Kafka partitions per topic. See LICENSE file for details.
 * For commercial use or more partitions and features, contact [https://www.taktx.io/contact].
 */

package io.taktx.client.serdes;

import static org.assertj.core.api.Assertions.assertThat;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.junit.jupiter.api.Test;

class JsonDeserializerTest {

  @Test
  void testSerializeDeserialize() {
    // Given
    try (JsonDeserializer<MyObject> deserializer = new JsonDeserializer<>(MyObject.class) {};
        JsonSerializer<MyObject> serializer = new JsonSerializer<>(MyObject.class) {}) {

      // When
      MyObject testValue = new MyObject("testValue");
      byte[] tests = serializer.serialize(null, testValue);
      MyObject result = deserializer.deserialize(null, tests);

      // Then
      assertThat(result).isEqualTo(testValue);
    }
  }

  @Data
  @NoArgsConstructor
  @AllArgsConstructor
  static class MyObject {

    private String field;
  }
}
