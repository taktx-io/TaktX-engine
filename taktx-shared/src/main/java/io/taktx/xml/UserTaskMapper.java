package io.taktx.xml;

import io.taktx.bpmn.TUserTask;
import io.taktx.dto.InputOutputMappingDTO;
import io.taktx.dto.LoopCharacteristicsDTO;
import io.taktx.dto.UserTaskDTO;

public interface UserTaskMapper extends Mapper {
  String DEFAULT_RETRIES = "3";

  UserTaskDTO map(
      TUserTask userTask,
      String parentId,
      LoopCharacteristicsDTO loopCharacteristics,
      InputOutputMappingDTO ioMapping);
}
