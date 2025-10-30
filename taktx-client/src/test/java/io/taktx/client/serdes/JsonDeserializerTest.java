/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks All rights reserved.
 * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 * Free use is permitted with up to 3 Kafka partitions per topic. See LICENSE file for details.
 * For commercial use or more partitions and features, contact [https://www.taktx.io/contact].
 */

package io.taktx.client.serdes;

import static org.assertj.core.api.Assertions.assertThat;

import io.taktx.serdes.JsonDeserializer;
import io.taktx.serdes.JsonSerializer;
import java.util.Objects;
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

  static class MyObject {

    private String field;

    public MyObject() {}

    public MyObject(String field) {
      this.field = field;
    }

    @Override
    public boolean equals(Object o) {
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      MyObject myObject = (MyObject) o;
      return Objects.equals(field, myObject.field);
    }

    @Override
    public int hashCode() {
      return Objects.hashCode(field);
    }

    public String getField() {
      return field;
    }

    public void setField(String field) {
      this.field = field;
    }
  }
}
