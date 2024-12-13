package com.flomaestro.takt.util;

import java.nio.ByteBuffer;
import java.util.UUID;

public class TaktCompositeUUIDSerializer
    implements org.apache.kafka.common.serialization.Serializer<java.util.UUID[]> {

  @Override
  public byte[] serialize(String topic, UUID... data) {
    ByteBuffer bb = ByteBuffer.allocate(16 * data.length);
    for (UUID uuid : data) {
      bb.putLong(uuid.getMostSignificantBits());
      bb.putLong(uuid.getLeastSignificantBits());
    }
    return bb.array();
  }
}
