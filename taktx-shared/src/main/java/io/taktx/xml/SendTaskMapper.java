package io.taktx.xml;

import io.taktx.bpmn.TSendTask;
import io.taktx.dto.v_1_0_0.InputOutputMappingDTO;
import io.taktx.dto.v_1_0_0.LoopCharacteristicsDTO;
import io.taktx.dto.v_1_0_0.SendTaskDTO;

public interface SendTaskMapper extends Mapper {
  String DEFAULT_RETRIES = "3";

  SendTaskDTO map(
      TSendTask serviceTask,
      String parentId,
      LoopCharacteristicsDTO loopCharacteristics,
      InputOutputMappingDTO ioMapping);
}
