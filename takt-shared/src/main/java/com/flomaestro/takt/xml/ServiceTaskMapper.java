package com.flomaestro.takt.xml;

import com.flomaestro.bpmn.TServiceTask;
import com.flomaestro.takt.dto.v_1_0_0.InputOutputMappingDTO;
import com.flomaestro.takt.dto.v_1_0_0.LoopCharacteristicsDTO;
import com.flomaestro.takt.dto.v_1_0_0.ServiceTaskDTO;

public interface ServiceTaskMapper extends Mapper {
  String DEFAULT_RETRIES = "3";

  ServiceTaskDTO map(
      TServiceTask serviceTask,
      String parentId,
      LoopCharacteristicsDTO loopCharacteristics,
      InputOutputMappingDTO ioMapping);
}
