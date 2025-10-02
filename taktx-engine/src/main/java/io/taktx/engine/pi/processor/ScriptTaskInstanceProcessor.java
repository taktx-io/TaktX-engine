/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks All rights reserved.
 * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 * Free use is permitted with up to 3 Kafka partitions per topic. See LICENSE file for details.
 * For commercial use or more partitions and features, contact [https://www.taktx.io/contact].
 */

package io.taktx.engine.pi.processor;

import com.fasterxml.jackson.databind.JsonNode;
import io.taktx.dto.ExecutionState;
import io.taktx.dto.ExternalTaskResponseTriggerDTO;
import io.taktx.dto.ScriptType;
import io.taktx.engine.feel.FeelExpressionHandler;
import io.taktx.engine.pd.model.ScriptTask;
import io.taktx.engine.pi.ProcessInstanceMapper;
import io.taktx.engine.pi.ProcessInstanceProcessingContext;
import io.taktx.engine.pi.model.Scope;
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
    extends ExternalTaskInstanceProcessor<ScriptTask, ScriptTaskInstance> {

  @Inject
  protected ScriptTaskInstanceProcessor(
      FeelExpressionHandler feelExpressionHandler,
      Clock clock,
      IoMappingProcessor ioMappingProcessor,
      ProcessInstanceMapper processInstanceMapper) {
    super(feelExpressionHandler, clock, ioMappingProcessor, processInstanceMapper);
  }

  @Override
  protected void processStartSpecificActivityInstance(
      ProcessInstanceProcessingContext processInstanceProcessingContext,
      Scope scope,
      ScriptTaskInstance flownodeInstance,
      String inputFlowId) {
    ScriptType scriptType = flownodeInstance.getFlowNode().getScriptType();
    if (scriptType == ScriptType.FEEL) {
      String expression = flownodeInstance.getFlowNode().getScriptExpressions().getFirst();
      VariableScope variableScope = scope.getVariableScope();
      JsonNode jsonNode = feelExpressionHandler.processFeelExpression(expression, variableScope);
      variableScope.put(flownodeInstance.getFlowNode().getResultVariableName(), jsonNode);
      flownodeInstance.setState(ExecutionState.COMPLETED);
    } else if (scriptType == ScriptType.JOBWORKER) {
      super.processStartSpecificActivityInstance(
          processInstanceProcessingContext, scope, flownodeInstance, inputFlowId);
    }
  }

  @Override
  protected void processContinueSpecificActivityInstance(
      ProcessInstanceProcessingContext processInstanceProcessingContext,
      Scope scope,
      ScriptTaskInstance externalTaskInstance,
      ExternalTaskResponseTriggerDTO trigger) {
    ScriptType scriptType = externalTaskInstance.getFlowNode().getScriptType();
    if (scriptType == ScriptType.JOBWORKER) {
      super.processContinueSpecificActivityInstance(
          processInstanceProcessingContext, scope, externalTaskInstance, trigger);
    } else if (scriptType == ScriptType.FEEL) {
      // For FEEL scripts, we do not continue the instance, as it is already finished
      log.warn(
          "Script task {} with FEEL script type is already finished and cannot be continued.",
          externalTaskInstance.getFlowNode().getId());
    }
  }

  @Override
  protected void processTerminateSpecificActivityInstance(
      ProcessInstanceProcessingContext processInstanceProcessingContext,
      Scope scope,
      ScriptTaskInstance instance) {
    ScriptType scriptType = instance.getFlowNode().getScriptType();
    if (scriptType == ScriptType.JOBWORKER) {
      super.processTerminateSpecificActivityInstance(
          processInstanceProcessingContext, scope, instance);
    } else if (scriptType == ScriptType.FEEL) {
      // For FEEL scripts, we do not continue the instance, as it is already finished
      log.warn(
          "Script task {} with FEEL script type is already finished and cannot be continued.",
          instance.getFlowNode().getId());
    }
  }
}
