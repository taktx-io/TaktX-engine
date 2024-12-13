package com.flomaestro.takt.util;

import com.flomaestro.takt.dto.v_1_0_0.VariableKeyDTO;
import java.util.UUID;
import org.apache.kafka.common.serialization.Serializer;
import org.apache.kafka.common.serialization.StringSerializer;

public class VariableKeySerializer implements Serializer<VariableKeyDTO> {
  private final TaktUUIDSerializer uuidSerializer = new TaktUUIDSerializer();
  private final StringSerializer stringSerializer = new StringSerializer();

  @Override
  public byte[] serialize(String topic, VariableKeyDTO data) {
    if (data == null) {
      return null;
    }

    UUID processInstanceKey = data.getProcessInstanceKey();
    byte[] serializedUUID = uuidSerializer.serialize(topic, processInstanceKey);
    byte[] serializedVariableName = stringSerializer.serialize(topic, data.getVariableName());
    // Return a concatenated byte array of the serialized UUID and the serialized variable name
    byte[] serializedVariableKey = new byte[serializedUUID.length + serializedVariableName.length];
    System.arraycopy(serializedUUID, 0, serializedVariableKey, 0, serializedUUID.length);
    System.arraycopy(
        serializedVariableName,
        0,
        serializedVariableKey,
        serializedUUID.length,
        serializedVariableName.length);
    return serializedVariableKey;
  }
}
