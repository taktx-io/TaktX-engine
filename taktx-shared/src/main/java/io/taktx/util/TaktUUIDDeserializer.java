package io.taktx.util;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.UUID;
import org.apache.kafka.common.serialization.Deserializer;

public class TaktUUIDDeserializer extends JsonDeserializer<UUID> implements Deserializer<UUID> {
  @Override
  public UUID deserialize(JsonParser jsonParser, DeserializationContext deserializationContext)
      throws IOException {
    byte[] bytes = jsonParser.getBinaryValue();
    return getUuid(bytes);
  }

  private static UUID getUuid(byte[] bytes) {
    ByteBuffer buffer = ByteBuffer.wrap(bytes);
    long mostSigBits = buffer.getLong();
    long leastSigBits = buffer.getLong();
    return new UUID(mostSigBits, leastSigBits);
  }

  @Override
  public UUID deserialize(String s, byte[] bytes) {
    return getUuid(bytes);
  }
}
