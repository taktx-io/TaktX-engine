package io.taktx.xml;

import io.taktx.bpmn.TUserTask;
import io.taktx.dto.InputOutputMappingDTO;
import io.taktx.dto.LoopCharacteristicsDTO;
import io.taktx.dto.UserTaskDTO;
import io.taktx.dto.UserTaskTypeEnum;
import java.util.Map;

public class GenericUserTaskMapper implements UserTaskMapper {

  @Override
  public UserTaskDTO map(
      TUserTask userTask,
      String parentId,
      LoopCharacteristicsDTO loopCharacteristics,
      InputOutputMappingDTO ioMapping) {
    return new UserTaskDTO(
        userTask.getId(),
        parentId,
        mapQNameList(userTask.getIncoming()),
        mapQNameList(userTask.getOutgoing()),
        loopCharacteristics,
        ioMapping,
        Map.of(),
        UserTaskTypeEnum.JOBWORKER,
        null,
        null,
        null
        );
  }
}
