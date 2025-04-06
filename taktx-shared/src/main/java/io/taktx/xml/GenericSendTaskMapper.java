package io.taktx.xml;

import io.taktx.bpmn.TSendTask;
import io.taktx.dto.v_1_0_0.InputOutputMappingDTO;
import io.taktx.dto.v_1_0_0.LoopCharacteristicsDTO;
import io.taktx.dto.v_1_0_0.SendTaskDTO;
import java.util.Map;

public class GenericSendTaskMapper implements SendTaskMapper {

  @Override
  public SendTaskDTO map(
      TSendTask sendTask,
      String parentId,
      LoopCharacteristicsDTO loopCharacteristics,
      InputOutputMappingDTO ioMapping) {
    return new SendTaskDTO(
        sendTask.getId(),
        parentId,
        sendTask.getId(),
        DEFAULT_RETRIES,
        mapQNameList(sendTask.getIncoming()),
        mapQNameList(sendTask.getOutgoing()),
        sendTask.getImplementation(),
        loopCharacteristics,
        Map.of(),
        ioMapping);
  }
}
