package nl.qunit.bpmnmeister.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import org.apache.kafka.common.serialization.Serializer;

public abstract class JsonSerializer<T> implements Serializer<T> {

  private final Class<T> clazz;

  JsonSerializer(Class<T> clazz) {
    this.clazz = clazz;
  }

  @Override
  public byte[] serialize(String topic, T data) {
    try {
      return new ObjectMapper().writeValueAsBytes(data);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
}
