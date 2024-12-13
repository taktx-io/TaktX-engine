package com.flomaestro.takt.util;

import java.util.UUID;
import org.apache.kafka.common.serialization.Deserializer;
import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.Serializer;

public class TaktUUIDSerde implements Serde<UUID> {

  public static final UUID MIN_UUID = new UUID(0x0000000000000000L, 0x0000000000000000L);
  public static final UUID MAX_UUID = new UUID(0xffffffffffffffffL, 0xffffffffffffffffL);

  private final Serializer<UUID> serializer;
  private final Deserializer<UUID> deserializer;

  public TaktUUIDSerde() {
    serializer = new TaktUUIDSerializer();
    deserializer = new TaktUUIDDeserializer();
  }

  @Override
  public Serializer<UUID> serializer() {
    return serializer;
  }

  @Override
  public Deserializer<UUID> deserializer() {
    return deserializer;
  }
}
