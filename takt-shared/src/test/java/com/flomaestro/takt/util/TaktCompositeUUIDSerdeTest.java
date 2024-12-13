package com.flomaestro.takt.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class TaktCompositeUUIDSerdeTest {

  @Test
  void test() {
    UUID uuid1 = UUID.randomUUID();
    UUID uuid2 = UUID.randomUUID();
    TaktCompositeUUIDSerde serde = new TaktCompositeUUIDSerde();

    byte[] serialized = serde.serializer().serialize("topic", new UUID[] {uuid1, uuid2});
    UUID[] deserialized = serde.deserializer().deserialize("topic", serialized);
    assertEquals(uuid1, deserialized[0]);
    assertEquals(uuid2, deserialized[1]);
  }
}
