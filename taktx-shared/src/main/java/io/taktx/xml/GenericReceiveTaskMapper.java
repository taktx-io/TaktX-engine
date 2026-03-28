/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
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
