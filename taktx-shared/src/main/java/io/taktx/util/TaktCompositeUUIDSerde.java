package io.taktx.util;

import java.util.UUID;
import org.apache.kafka.common.serialization.Deserializer;
import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.Serializer;

public class TaktCompositeUUIDSerde implements Serde<UUID[]> {

  private final Serializer<UUID[]> serializer;
  private final Deserializer<UUID[]> deserializer;

  public TaktCompositeUUIDSerde() {
    serializer = new TaktCompositeUUIDSerializer();
    deserializer = new TaktCompositeUUIDDeserializer();
  }

  @Override
  public Serializer<UUID[]> serializer() {
    return serializer;
  }

  @Override
  public Deserializer<UUID[]> deserializer() {
    return deserializer;
  }
}
