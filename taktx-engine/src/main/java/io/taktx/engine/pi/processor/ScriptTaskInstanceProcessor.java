/*
 *
 *  * TaktX - A high-performance BPMN engine
 *  * Copyright (c) 2025 TaktX B.V. All rights reserved.
 *  * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 *  * Free use is permitted with up to 3 Kafka partitions. See LICENSE file for details.
 *  * For commercial use or more partitions and features, contact [info@taktx.io] or [https://www.taktx.io/contact].
 *
 */

package io.taktx.engine.pi.processor;

import com.fasterxml.jackson.databind.JsonNode;
import io.taktx.dto.ActtivityStateEnum;
import io.taktx.dto.ContinueFlowElementTriggerDTO;
import io.taktx.dto.ScriptType;
import io.taktx.engine.feel.FeelExpressionHandler;
import io.taktx.engine.pd.model.ScriptTask;
import io.taktx.engine.pi.DirectInstanceResult;
import io.taktx.engine.pi.FlowNodeInstanceProcessingContext;
import io.taktx.engine.pi.ProcessInstanceMapper;
import io.taktx.engine.pi.ProcessInstanceProcessingContext;
import io.taktx.engine.pi.model.ScriptTaskInstance;
import io.taktx.engine.pi.model.VariableScope;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.Clock;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@ApplicationScoped
@NoArgsConstructor
@Slf4j
public class ScriptTaskInstanceProcessor
    extends ActivityInstanceProcessor<
        ScriptTask, ScriptTaskInstance, ContinueFlowElementTriggerDTO> {

  @Inject
  protected ScriptTaskInstanceProcessor(
      FeelExpressionHandler feelExpressionHandler,
      Clock clock,
      IoMappingProcessor ioMappingProcessor,
      ProcessInstanceMapper processInstanceMapper) {
    super(feelExpressionHandler, ioMappingProcessor, processInstanceMapper, clock);
  }

  @Override
  protected void processStartSpecificActivityInstance(
      ProcessInstanceProcessingContext processInstanceProcessingContext,
      FlowNodeInstanceProcessingContext flowNodeInstanceProcessingContext,
      ScriptTaskInstance flownodeInstance,
      String inputFlowId,
      VariableScope variables) {
    ScriptType scriptType = flownodeInstance.getFlowNode().getScriptType();
    if (scriptType == ScriptType.FEEL) {
      String expression = flownodeInstance.getFlowNode().getScriptExpressions().get(0);
      JsonNode jsonNode = feelExpressionHandler.processFeelExpression(expression, variables);
      variables.put(flownodeInstance.getFlowNode().getResultVariableName(), jsonNode);
    }
    flownodeInstance.setState(ActtivityStateEnum.FINISHED);
  }

  @Override
  protected void processContinueSpecificActivityInstance(
      ProcessInstanceProcessingContext processInstanceProcessingContext,
      FlowNodeInstanceProcessingContext flowNodeInstanceProcessingContext,
      int subProcessLevel,
      ScriptTaskInstance externalTaskInstance,
      ContinueFlowElementTriggerDTO trigger,
      VariableScope flowNodeInstanceVariables) {
    // do nothing here, the script task is already finished
  }

  @Override
  protected void processTerminateSpecificActivityInstance(
      ProcessInstanceProcessingContext processInstanceProcessingContext,
      DirectInstanceResult directInstanceResult,
      ScriptTaskInstance instance,
      VariableScope variables) {
    // Nothing to do here
  }
}
