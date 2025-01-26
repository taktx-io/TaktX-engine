package com.flomaestro.takt.util;

import com.flomaestro.takt.dto.v_1_0_0.VariableKeyDTO;
import java.nio.ByteBuffer;
import java.util.UUID;
import org.apache.kafka.common.serialization.Deserializer;

public class VariableKeyDeserializer implements Deserializer<VariableKeyDTO> {
  private final TaktUUIDDeserializer uuidDeserializer = new TaktUUIDDeserializer();

  @Override
  public VariableKeyDTO deserialize(String topic, byte[] data) {
    if (data == null) {
      return null;
    }
    UUID processInstanceKey =
        uuidDeserializer.deserialize(topic, ByteBuffer.wrap(data, 0, 16).array());
    UUID flowNodeInstancesKey =
        uuidDeserializer.deserialize(topic, ByteBuffer.wrap(data, 16, 16).array());
    UUID flowNodeInstanceKey =
        uuidDeserializer.deserialize(topic, ByteBuffer.wrap(data, 32, 16).array());

    String variableName = new String(data, 48, data.length - 48);
    return new VariableKeyDTO(
        processInstanceKey, flowNodeInstancesKey, flowNodeInstanceKey, variableName);
  }
}
