package io.taktx.xml;

import io.taktx.bpmn.TServiceTask;
import io.taktx.dto.v_1_0_0.InputOutputMappingDTO;
import io.taktx.dto.v_1_0_0.LoopCharacteristicsDTO;
import io.taktx.dto.v_1_0_0.ServiceTaskDTO;

public interface ServiceTaskMapper extends Mapper {
  String DEFAULT_RETRIES = "3";

  ServiceTaskDTO map(
      TServiceTask serviceTask,
      String parentId,
      LoopCharacteristicsDTO loopCharacteristics,
      InputOutputMappingDTO ioMapping);
}
