/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.xml;

import io.taktx.bpmn.CalledDecision;
import io.taktx.bpmn.TBusinessRuleTask;
import io.taktx.dto.BusinessRuleTaskDTO;
import io.taktx.dto.InputOutputMappingDTO;
import io.taktx.dto.LoopCharacteristicsDTO;
import java.util.Optional;

public class ZeebeBusinessRuleTaskMapper implements BusinessRuleTaskMapper {
  @Override
  public BusinessRuleTaskDTO map(
      TBusinessRuleTask task,
      String parentId,
      LoopCharacteristicsDTO loopCharacteristics,
      InputOutputMappingDTO ioMapping) {
    String decisionId = "";
    String resultVariable = "";

    Optional<CalledDecision> calledDecision =
        ExtensionElementHelper.extractExtensionElement(
            task.getExtensionElements(), CalledDecision.class);
    if (calledDecision.isPresent()) {
      decisionId = calledDecision.get().getDecisionId();
      resultVariable = calledDecision.get().getResultVariable();
    }

    return new BusinessRuleTaskDTO(
        task.getId(),
        parentId,
        task.getName(),
        mapQNameList(task.getIncoming()),
        mapQNameList(task.getOutgoing()),
        loopCharacteristics,
        ioMapping,
        decisionId,
        resultVariable);
  }
}
