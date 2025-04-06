package io.taktx.xml;

import io.taktx.bpmn.TReceiveTask;
import io.taktx.dto.v_1_0_0.InputOutputMappingDTO;
import io.taktx.dto.v_1_0_0.LoopCharacteristicsDTO;
import io.taktx.dto.v_1_0_0.ReceiveTaskDTO;

public interface ReceiveTaskMapper extends Mapper {

  ReceiveTaskDTO map(
      TReceiveTask serviceTask,
      String parentId,
      LoopCharacteristicsDTO loopCharacteristics,
      InputOutputMappingDTO ioMapping);
}
