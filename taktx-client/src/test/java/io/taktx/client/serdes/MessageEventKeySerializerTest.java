package io.taktx.client.serdes;

import static org.assertj.core.api.Assertions.assertThat;

import io.taktx.dto.v_1_0_0.MessageEventKeyDTO;
import org.junit.jupiter.api.Test;

class MessageEventKeySerializerTest {

  @Test
  void testConstruct() {
    try (MessageEventKeySerializer serializer = new MessageEventKeySerializer()) {
      assertThat(serializer.getClazz()).isEqualTo(MessageEventKeyDTO.class);
    }
  }
}
