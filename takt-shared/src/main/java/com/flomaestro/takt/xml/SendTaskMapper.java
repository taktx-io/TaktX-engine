package com.flomaestro.takt.xml;

import com.flomaestro.bpmn.TSendTask;
import com.flomaestro.takt.dto.v_1_0_0.InputOutputMappingDTO;
import com.flomaestro.takt.dto.v_1_0_0.LoopCharacteristicsDTO;
import com.flomaestro.takt.dto.v_1_0_0.SendTaskDTO;

public interface SendTaskMapper extends Mapper {
  String DEFAULT_RETRIES = "3";

  SendTaskDTO map(
      TSendTask serviceTask,
      String parentId,
      LoopCharacteristicsDTO loopCharacteristics,
      InputOutputMappingDTO ioMapping);
}
