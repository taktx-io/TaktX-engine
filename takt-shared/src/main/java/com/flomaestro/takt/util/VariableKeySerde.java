package com.flomaestro.takt.util;

import com.flomaestro.takt.dto.v_1_0_0.VariableKeyDTO;
import org.apache.kafka.common.serialization.Deserializer;
import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.Serializer;

public class VariableKeySerde implements Serde<VariableKeyDTO> {

  private final Deserializer<VariableKeyDTO> deserializer = new VariableKeyDeserializer();
  private final Serializer<VariableKeyDTO> serializer = new VariableKeySerializer();

  @Override
  public Serializer<VariableKeyDTO> serializer() {
    return serializer;
  }

  @Override
  public Deserializer<VariableKeyDTO> deserializer() {
    return deserializer;
  }
}
