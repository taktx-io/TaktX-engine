/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
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
        userTask.getName(),
        mapQNameList(userTask.getIncoming()),
        mapQNameList(userTask.getOutgoing()),
        loopCharacteristics,
        ioMapping,
        Map.of(),
        UserTaskTypeEnum.JOBWORKER,
        null,
        null,
        null);
  }
}
