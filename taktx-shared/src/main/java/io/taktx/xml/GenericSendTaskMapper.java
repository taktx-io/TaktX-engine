package io.taktx.xml;

import io.taktx.bpmn.TSendTask;
import io.taktx.dto.InputOutputMappingDTO;
import io.taktx.dto.LoopCharacteristicsDTO;
import io.taktx.dto.SendTaskDTO;
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
