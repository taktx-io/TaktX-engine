/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks All rights reserved.
 * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 * Free use is permitted with up to 3 Kafka partitions per topic. See LICENSE file for details.
 * For commercial use or more partitions and features, contact [https://www.taktx.io/contact].
 */

package io.taktx.xml;

import io.taktx.bpmn.TReceiveTask;
import io.taktx.dto.InputOutputMappingDTO;
import io.taktx.dto.LoopCharacteristicsDTO;
import io.taktx.dto.ReceiveTaskDTO;
import java.util.Set;

public class GenericReceiveTaskMapper implements ReceiveTaskMapper {

  @Override
  public ReceiveTaskDTO map(
      TReceiveTask receiveTask,
      String parentId,
      LoopCharacteristicsDTO loopCharacteristics,
      InputOutputMappingDTO ioMapping) {

    Set<String> incoming = mapQNameList(receiveTask.getIncoming());
    Set<String> outgoing = mapQNameList(receiveTask.getOutgoing());

    return new ReceiveTaskDTO(
        receiveTask.getId(),
        parentId,
        receiveTask.getName(),
        incoming,
        outgoing,
        loopCharacteristics,
        receiveTask.getMessageRef().getLocalPart(),
        ioMapping);
  }
}
