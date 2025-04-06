package io.taktx.engine.pi.testengine;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.cbor.CBORFactory;
import io.quarkus.kafka.client.serialization.ObjectMapperSerializer;

public class CBORObjectMapperSerializer<T> extends ObjectMapperSerializer<T> {

  public CBORObjectMapperSerializer() {
    super(new ObjectMapper(new CBORFactory()));
  }
}
