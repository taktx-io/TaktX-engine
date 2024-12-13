package com.flomaestro.takt.util;

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
