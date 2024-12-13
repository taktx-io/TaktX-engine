package com.flomaestro.takt.util;

import java.nio.ByteBuffer;
import java.util.UUID;

public class TaktUUIDDeserializer
    implements org.apache.kafka.common.serialization.Deserializer<UUID> {

  @Override
  public UUID deserialize(String topic, byte[] data) {
    ByteBuffer bb = ByteBuffer.wrap(data);
    long msb = bb.getLong();
    long lsb = bb.getLong();
    return new UUID(msb, lsb);
  }
}
