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
