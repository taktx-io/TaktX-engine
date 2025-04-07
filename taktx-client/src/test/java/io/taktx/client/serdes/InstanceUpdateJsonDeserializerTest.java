package io.taktx.client.serdes;

import static org.assertj.core.api.Assertions.assertThat;

import io.taktx.dto.v_1_0_0.InstanceUpdateDTO;
import org.junit.jupiter.api.Test;

class InstanceUpdateJsonDeserializerTest {
  @Test
  void testConstruct() {
    try (InstanceUpdateJsonDeserializer deserializer = new InstanceUpdateJsonDeserializer()) {
      assertThat(deserializer.getClazz()).isEqualTo(InstanceUpdateDTO.class);
    }
  }
}
