package com.flomaestro.takt.xml;

import com.flomaestro.bpmn.TReceiveTask;
import com.flomaestro.takt.dto.v_1_0_0.InputOutputMappingDTO;
import com.flomaestro.takt.dto.v_1_0_0.LoopCharacteristicsDTO;
import com.flomaestro.takt.dto.v_1_0_0.ReceiveTaskDTO;

public interface ReceiveTaskMapper extends Mapper {

  ReceiveTaskDTO map(
      TReceiveTask serviceTask,
      String parentId,
      LoopCharacteristicsDTO loopCharacteristics,
      InputOutputMappingDTO ioMapping);
}
