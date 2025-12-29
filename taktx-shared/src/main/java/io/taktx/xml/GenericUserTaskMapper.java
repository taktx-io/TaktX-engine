/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks All rights reserved.
 * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 * Free use is permitted with up to 3 Kafka partitions per topic. See LICENSE file for details.
 * For commercial use or more partitions and features, contact [https://www.taktx.io/contact].
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
