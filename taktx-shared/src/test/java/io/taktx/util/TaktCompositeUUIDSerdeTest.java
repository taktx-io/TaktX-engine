/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.util;

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
    serde.close();
  }
}
