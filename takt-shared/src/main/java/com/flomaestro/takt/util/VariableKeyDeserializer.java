package com.flomaestro.takt.util;

import com.flomaestro.takt.dto.v_1_0_0.VariableKeyDTO;
import java.util.Arrays;
import java.util.UUID;
import org.apache.kafka.common.serialization.Deserializer;

public class VariableKeyDeserializer implements Deserializer<VariableKeyDTO> {
  private final TaktUUIDDeserializer uuidDeserializer = new TaktUUIDDeserializer();

  @Override
  public VariableKeyDTO deserialize(String topic, byte[] data) {
    if (data == null) {
      return null;
    }
    UUID processInstanceKey = uuidDeserializer.deserialize(topic, Arrays.copyOfRange(data, 0, 16));
    UUID flowNodeInstancesKey =
        uuidDeserializer.deserialize(topic, Arrays.copyOfRange(data, 16, 32));
    UUID flowNodeInstanceKey =
        uuidDeserializer.deserialize(topic, Arrays.copyOfRange(data, 32, 48));

    String variableName = new String(data, 48, data.length - 48);
    return new VariableKeyDTO(
        processInstanceKey, flowNodeInstancesKey, flowNodeInstanceKey, variableName);
  }
}
