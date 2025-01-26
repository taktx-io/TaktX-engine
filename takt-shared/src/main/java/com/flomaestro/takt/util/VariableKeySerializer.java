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

    UUID processInstanceKey = data.getProcessInstanceId();
    byte[] serializedProcessInstanceId = uuidSerializer.serialize(topic, processInstanceKey);
    byte[] serializedFlowNodeInstancesId = uuidSerializer.serialize(topic, data.getFlowNodeInstancesId());
    byte[] serializedEleeentInstanceId = uuidSerializer.serialize(topic, data.getElementInstanceId());
    byte[] serializedVariableName = stringSerializer.serialize(topic, data.getVariableName());
    // Return a concatenated byte array of the serialized UUID and the serialized variable name
    byte[] serializedVariableKey = new byte[serializedProcessInstanceId.length +
        serializedFlowNodeInstancesId.length +
        serializedEleeentInstanceId.length +
        serializedVariableName.length];
    int index = 0;
    System.arraycopy(serializedProcessInstanceId, 0, serializedVariableKey, index, serializedProcessInstanceId.length);
    index += serializedProcessInstanceId.length;
    System.arraycopy(serializedFlowNodeInstancesId, 0, serializedVariableKey, index, serializedFlowNodeInstancesId.length);
    index += serializedFlowNodeInstancesId.length;
    System.arraycopy(serializedEleeentInstanceId, 0, serializedVariableKey, index, serializedEleeentInstanceId.length);
    index += serializedFlowNodeInstancesId.length;
    System.arraycopy(
        serializedVariableName,
        0,
        serializedVariableKey,
        index,
        serializedVariableName.length);
    return serializedVariableKey;
  }
}
