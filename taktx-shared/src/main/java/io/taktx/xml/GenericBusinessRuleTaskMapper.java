/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.xml;

import io.taktx.bpmn.TBusinessRuleTask;
import io.taktx.dto.BusinessRuleTaskDTO;
import io.taktx.dto.InputOutputMappingDTO;
import io.taktx.dto.LoopCharacteristicsDTO;

public class GenericBusinessRuleTaskMapper implements BusinessRuleTaskMapper {
  @Override
  public BusinessRuleTaskDTO map(
      TBusinessRuleTask task,
      String parentId,
      LoopCharacteristicsDTO loopCharacteristics,
      InputOutputMappingDTO ioMapping) {
    return new BusinessRuleTaskDTO(
        task.getId(),
        parentId,
        task.getName(),
        mapQNameList(task.getIncoming()),
        mapQNameList(task.getOutgoing()),
        loopCharacteristics,
        ioMapping,
        "",
        "");
  }
}
