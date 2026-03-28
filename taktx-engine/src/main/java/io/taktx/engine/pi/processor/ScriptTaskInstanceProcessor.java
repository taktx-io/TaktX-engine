/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
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
      VariableScope variableScope,
      ScriptTaskInstance flownodeInstance,
      String inputFlowId) {
    ScriptType scriptType = flownodeInstance.getFlowNode().getScriptType();
    if (scriptType == ScriptType.FEEL) {
      String expression = flownodeInstance.getFlowNode().getScriptExpressions().getFirst();
      JsonNode jsonNode = feelExpressionHandler.processFeelExpression(expression, variableScope);
      variableScope.put(flownodeInstance.getFlowNode().getResultVariableName(), jsonNode);
      flownodeInstance.setState(ExecutionState.COMPLETED);
    } else if (scriptType == ScriptType.JOBWORKER) {
      super.processStartSpecificActivityInstance(
          processInstanceProcessingContext, scope, variableScope, flownodeInstance, inputFlowId);
    }
  }

  @Override
  protected void processContinueSpecificActivityInstance(
      ProcessInstanceProcessingContext processInstanceProcessingContext,
      Scope scope,
      VariableScope variableScope,
      ScriptTaskInstance externalTaskInstance,
      ExternalTaskResponseTriggerDTO trigger) {
    ScriptType scriptType = externalTaskInstance.getFlowNode().getScriptType();
    if (scriptType == ScriptType.JOBWORKER) {
      super.processContinueSpecificActivityInstance(
          processInstanceProcessingContext, scope, variableScope, externalTaskInstance, trigger);
    } else if (scriptType == ScriptType.FEEL) {
      // For FEEL scripts, we do not continue the instance, as it is already finished
      log.warn(
          "Script task {} with FEEL script type is already finished and cannot be continued.",
          externalTaskInstance.getFlowNode().getId());
    }
  }

  @Override
  protected void processAbortSpecificActivityInstance(
      ProcessInstanceProcessingContext processInstanceProcessingContext,
      Scope scope,
      VariableScope variableScope,
      ScriptTaskInstance instance) {
    ScriptType scriptType = instance.getFlowNode().getScriptType();
    if (scriptType == ScriptType.JOBWORKER) {
      super.processAbortSpecificActivityInstance(
          processInstanceProcessingContext, scope, variableScope, instance);
    } else if (scriptType == ScriptType.FEEL) {
      // For FEEL scripts, we do not continue the instance, as it is already finished
      log.warn(
          "Script task {} with FEEL script type is already finished and cannot be continued.",
          instance.getFlowNode().getId());
    }
  }
}
