/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks All rights reserved.
 * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 * Free use is permitted with up to 3 Kafka partitions per topic. See LICENSE file for details.
 * For commercial use or more partitions and features, contact [https://www.taktx.io/contact].
 */

package io.taktx.xml;

import io.taktx.bpmn.TSendTask;
import io.taktx.dto.InputOutputMappingDTO;
import io.taktx.dto.LoopCharacteristicsDTO;
import io.taktx.dto.SendTaskDTO;
import java.util.Map;

public class GenericSendTaskMapper implements SendTaskMapper {

  @Override
  public SendTaskDTO map(
      TSendTask sendTask,
      String parentId,
      LoopCharacteristicsDTO loopCharacteristics,
      InputOutputMappingDTO ioMapping) {
    return new SendTaskDTO(
        sendTask.getId(),
        parentId,
        sendTask.getId(),
        DEFAULT_RETRIES,
        mapQNameList(sendTask.getIncoming()),
        mapQNameList(sendTask.getOutgoing()),
        sendTask.getImplementation(),
        loopCharacteristics,
        Map.of(),
        ioMapping);
  }
}
