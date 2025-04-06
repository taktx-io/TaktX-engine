package io.taktx.client.serdes;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.cbor.CBORFactory;
import java.io.IOException;
import org.apache.kafka.common.serialization.Deserializer;

public abstract class JsonDeserializer<T> implements Deserializer<T> {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper(new CBORFactory());

  private final Class<T> clazz;

  JsonDeserializer(Class<T> clazz) {
    this.clazz = clazz;
  }

  @Override
  public T deserialize(String s, byte[] bytes) {
    try {
      return OBJECT_MAPPER.readValue(bytes, clazz);
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
  }
}
