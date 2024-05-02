package nl.qunit.bpmnmeister.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import org.apache.kafka.common.serialization.Deserializer;

public abstract class JsonDeserializer<T> implements Deserializer<T> {

  private final Class<T> clazz;
  private final ObjectMapper objectMapper;

  JsonDeserializer(Class<T> clazz, ObjectMapper objectMapper) {
    this.clazz = clazz;
    this.objectMapper = objectMapper;
  }

  @Override
  public T deserialize(String s, byte[] bytes) {
    try {
      return objectMapper.readValue(bytes, clazz);
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
  }
}
