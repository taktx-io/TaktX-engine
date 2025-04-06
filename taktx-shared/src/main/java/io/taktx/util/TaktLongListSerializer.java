package io.taktx.util;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;
import org.apache.kafka.common.serialization.Serializer;

public class TaktLongListSerializer extends JsonSerializer<List<Long>>
    implements Serializer<List<Long>> {
  @Override
  public void serialize(List<Long> uuid, JsonGenerator gen, SerializerProvider serializers)
      throws IOException {
    gen.writeBinary(toByteArray(uuid));
  }

  private static byte[] toByteArray(List<Long> longList) {
    ByteBuffer buffer = ByteBuffer.wrap(new byte[4 + 8 * longList.size()]);
    buffer.putInt(longList.size());
    for (int i = 0; i < longList.size(); i++) {
      buffer.putLong(longList.get(i));
    }
    return buffer.array();
  }

  @Override
  public byte[] serialize(String s, List<Long> uuid) {
    return toByteArray(uuid);
  }
}
