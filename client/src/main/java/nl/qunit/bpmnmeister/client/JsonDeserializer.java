package nl.qunit.bpmnmeister.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import org.apache.kafka.common.serialization.Deserializer;

public abstract class JsonDeserializer<T> implements Deserializer<T> {

  private final Class<T> clazz;
  private static final ObjectMapper objectMapper = new ObjectMapper();

  JsonDeserializer(Class<T> clazz) {
    this.clazz = clazz;
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
