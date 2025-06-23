/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks All rights reserved.
 * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 * Free use is permitted with up to 3 Kafka partitions per topic. See LICENSE file for details.
 * For commercial use or more partitions and features, contact [https://www.taktx.io/contact].
 */

package io.taktx.util;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.UUID;
import org.apache.kafka.common.serialization.Serializer;

public class TaktUUIDSerializer extends JsonSerializer<UUID> implements Serializer<UUID> {
  @Override
  public void serialize(UUID uuid, JsonGenerator gen, SerializerProvider serializers)
      throws IOException {
    gen.writeBinary(toByteArray(uuid));
  }

  private static byte[] toByteArray(UUID uuid) throws IOException {
    // Convert UUID to byte[]
    ByteBuffer buffer = ByteBuffer.wrap(new byte[16]);
    buffer.putLong(uuid.getMostSignificantBits());
    buffer.putLong(uuid.getLeastSignificantBits());
    return buffer.array();
  }

  @Override
  public byte[] serialize(String s, UUID uuid) {
    try {
      return toByteArray(uuid);
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
  }
}
