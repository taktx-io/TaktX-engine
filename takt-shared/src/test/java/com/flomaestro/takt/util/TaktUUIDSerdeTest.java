package com.flomaestro.takt.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class TaktUUIDSerdeTest {

  @Test
  void test() {
    UUID uuid1 = UUID.randomUUID();
    TaktUUIDSerde serde = new TaktUUIDSerde();

    byte[] serialized = serde.serializer().serialize("topic", uuid1);
    UUID deserialized = serde.deserializer().deserialize("topic", serialized);
    assertEquals(uuid1, deserialized);
  }
}
