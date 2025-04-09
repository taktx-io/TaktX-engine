package io.taktx.xml;

import io.taktx.bpmn.TSendTask;
import io.taktx.dto.InputOutputMappingDTO;
import io.taktx.dto.LoopCharacteristicsDTO;
import io.taktx.dto.SendTaskDTO;

public interface SendTaskMapper extends Mapper {
  String DEFAULT_RETRIES = "3";

  SendTaskDTO map(
      TSendTask serviceTask,
      String parentId,
      LoopCharacteristicsDTO loopCharacteristics,
      InputOutputMappingDTO ioMapping);
}
