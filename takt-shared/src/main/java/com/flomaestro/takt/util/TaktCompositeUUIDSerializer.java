/*
 *
 *  * TaktX - A high-performance BPMN engine
 *  * Copyright (c) 2025 TaktX B.V. All rights reserved.
 *  * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 *  * Free use is permitted with up to 3 Kafka partitions. See LICENSE file for details.
 *  * For commercial use or more partitions and features, contact [info@taktx.io] or [https://www.taktx.io/contact].
 *
 */

package com.flomaestro.takt.util;

import java.nio.ByteBuffer;
import java.util.UUID;

public class TaktCompositeUUIDSerializer
    implements org.apache.kafka.common.serialization.Serializer<java.util.UUID[]> {

  @Override
  public byte[] serialize(String topic, UUID[] data) {
    ByteBuffer bb = ByteBuffer.allocate(16 * data.length);
    for (UUID uuid : data) {
      bb.putLong(uuid.getMostSignificantBits());
      bb.putLong(uuid.getLeastSignificantBits());
    }
    return bb.array();
  }
}
