/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks All rights reserved.
 * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 * Free use is permitted with up to 3 Kafka partitions per topic. See LICENSE file for details.
 * For commercial use or more partitions and features, contact [https://www.taktx.io/contact].
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
