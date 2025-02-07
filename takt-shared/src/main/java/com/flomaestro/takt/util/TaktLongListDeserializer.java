package com.flomaestro.takt.util;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import org.apache.kafka.common.serialization.Deserializer;

public class TaktLongListDeserializer extends JsonDeserializer<List<Long>>
    implements Deserializer<List<Long>> {
  @Override
  public List<Long> deserialize(
      JsonParser jsonParser, DeserializationContext deserializationContext) throws IOException {
    byte[] bytes = jsonParser.getBinaryValue();
    return getUuid(bytes);
  }

  private static List<Long> getUuid(byte[] bytes) {
    List<Long> result = new ArrayList<>();
    ByteBuffer buffer = ByteBuffer.wrap(bytes);
    int size = buffer.getInt();
    for (int i = 0; i < size; i++) {
      long l = buffer.getLong();
      result.add(l);
    }
    return result;
  }

  @Override
  public List<Long> deserialize(String s, byte[] bytes) {
    return getUuid(bytes);
  }
}
