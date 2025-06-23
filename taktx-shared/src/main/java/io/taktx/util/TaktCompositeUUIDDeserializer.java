/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks All rights reserved.
 * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 * Free use is permitted with up to 3 Kafka partitions per topic. See LICENSE file for details.
 * For commercial use or more partitions and features, contact [https://www.taktx.io/contact].
 */

package io.taktx.util;

import java.nio.ByteBuffer;
import java.util.UUID;

public class TaktCompositeUUIDDeserializer
    implements org.apache.kafka.common.serialization.Deserializer<java.util.UUID[]> {

  @Override
  public UUID[] deserialize(String topic, byte[] data) {
    UUID[] uuids = new UUID[data.length / 16];
    int index = 0;
    ByteBuffer bb = ByteBuffer.wrap(data);
    while (bb.hasRemaining()) {
      long msb = bb.getLong();
      long lsb = bb.getLong();
      uuids[index++] = new UUID(msb, lsb);
    }
    return uuids;
  }
}
