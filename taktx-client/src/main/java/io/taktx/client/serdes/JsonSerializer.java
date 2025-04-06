package io.taktx.client.serdes;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.cbor.CBORFactory;
import java.io.IOException;
import org.apache.kafka.common.serialization.Serializer;

public abstract class JsonSerializer<T> implements Serializer<T> {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper(new CBORFactory());

  JsonSerializer(Class<T> clazz) {}

  @Override
  public byte[] serialize(String topic, T data) {
    try {
      return OBJECT_MAPPER.writeValueAsBytes(data);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
}
