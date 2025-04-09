package io.taktx.client.serdes;

import static org.assertj.core.api.Assertions.assertThat;

import io.taktx.dto.ExternalTaskTriggerDTO;
import org.junit.jupiter.api.Test;

class ExternalTaskTriggerJsonDeserializerTest {
  @Test
  void testConstruct() {
    try (ExternalTaskTriggerJsonDeserializer deserializer =
        new ExternalTaskTriggerJsonDeserializer()) {
      assertThat(deserializer.getClazz()).isEqualTo(ExternalTaskTriggerDTO.class);
    }
  }
}
