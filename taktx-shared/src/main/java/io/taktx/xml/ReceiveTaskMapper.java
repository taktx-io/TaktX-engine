package io.taktx.xml;

import io.taktx.bpmn.TReceiveTask;
import io.taktx.dto.InputOutputMappingDTO;
import io.taktx.dto.LoopCharacteristicsDTO;
import io.taktx.dto.ReceiveTaskDTO;

public interface ReceiveTaskMapper extends Mapper {

  ReceiveTaskDTO map(
      TReceiveTask serviceTask,
      String parentId,
      LoopCharacteristicsDTO loopCharacteristics,
      InputOutputMappingDTO ioMapping);
}
