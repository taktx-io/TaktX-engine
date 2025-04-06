package io.taktx.util;

import java.util.UUID;
import org.apache.kafka.common.serialization.Deserializer;
import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.Serializer;

public class TaktUUIDSerde implements Serde<UUID> {

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
