package com.flomaestro.takt.util;

import com.flomaestro.takt.dto.v_1_0_0.VariableKeyDTO;
import java.util.UUID;
import org.apache.kafka.common.serialization.Deserializer;

public class VariableKeyDeserializer implements Deserializer<VariableKeyDTO> {
  private final TaktUUIDDeserializer uuidDeserializer = new TaktUUIDDeserializer();

  @Override
  public VariableKeyDTO deserialize(String topic, byte[] data) {
    if (data == null) {
      return null;
    }
    UUID uuid = uuidDeserializer.deserialize(topic, data);
    String variableName = new String(data, 16, data.length - 16);
    return new VariableKeyDTO(uuid, variableName);
  }
}
