package io.taktx.client.serdes;

import static org.assertj.core.api.Assertions.assertThat;

import io.taktx.dto.v_1_0_0.StartCommandDTO;
import org.junit.jupiter.api.Test;

class StartCommandSerializerTest {
  @Test
  void testConstruct() {
    try (StartCommandSerializer serializer = new StartCommandSerializer()) {
      assertThat(serializer.getClazz()).isEqualTo(StartCommandDTO.class);
    }
  }
}
