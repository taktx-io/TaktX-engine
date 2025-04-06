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
