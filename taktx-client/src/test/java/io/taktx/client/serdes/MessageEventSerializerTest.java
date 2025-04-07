package io.taktx.client.serdes;

import static org.assertj.core.api.Assertions.assertThat;

import io.taktx.dto.v_1_0_0.MessageEventDTO;
import org.junit.jupiter.api.Test;

class MessageEventSerializerTest {

  @Test
  void testConstruct() {
    try (MessageEventSerializer serializer = new MessageEventSerializer()) {
      assertThat(serializer.getClazz()).isEqualTo(MessageEventDTO.class);
    }
  }
}
