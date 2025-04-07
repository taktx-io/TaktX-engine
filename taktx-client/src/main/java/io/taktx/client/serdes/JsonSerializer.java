package io.taktx.client.serdes;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.cbor.CBORFactory;
import java.io.IOException;
import lombok.Getter;
import org.apache.kafka.common.serialization.Serializer;

@Getter
public abstract class JsonSerializer<T> implements Serializer<T> {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper(new CBORFactory());

  private final Class<T> clazz;

  JsonSerializer(Class<T> clazz) {
    this.clazz = clazz;
  }

  @Override
  public byte[] serialize(String topic, T data) {
    try {
      return OBJECT_MAPPER.writeValueAsBytes(data);
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
  }
}
