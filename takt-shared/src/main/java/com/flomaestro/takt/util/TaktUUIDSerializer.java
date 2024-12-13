package com.flomaestro.takt.util;

import java.nio.ByteBuffer;
import java.util.UUID;

public class TaktUUIDSerializer implements org.apache.kafka.common.serialization.Serializer<UUID> {

  @Override
  public byte[] serialize(String topic, UUID uuid) {
    ByteBuffer bb = ByteBuffer.allocate(16);
    bb.putLong(uuid.getMostSignificantBits());
    bb.putLong(uuid.getLeastSignificantBits());
    return bb.array();
  }
}
