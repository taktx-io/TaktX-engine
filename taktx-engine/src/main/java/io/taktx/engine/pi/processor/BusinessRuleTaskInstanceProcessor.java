/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */

package io.taktx.engine.pi.processor;

import com.fasterxml.jackson.databind.JsonNode;
import io.taktx.dto.ContinueFlowElementTriggerDTO;
import io.taktx.dto.DmnDecisionDTO;
import io.taktx.dto.DmnDefinitionDTO;
import io.taktx.dto.ExecutionState;
import io.taktx.engine.dmn.DmnDecisionResolver;
import io.taktx.engine.dmn.DmnEvaluator;
import io.taktx.engine.feel.FeelExpressionHandler;
import io.taktx.engine.pd.model.BusinessRuleTask;
import io.taktx.engine.pi.ProcessInstanceMapper;
import io.taktx.engine.pi.ProcessInstanceProcessingContext;
import io.taktx.engine.pi.ProcessInstanceException;
import io.taktx.engine.pi.model.BusinessRuleTaskInstance;
import io.taktx.engine.pi.model.Scope;
import io.taktx.engine.pi.model.VariableScope;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.Clock;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@ApplicationScoped
@NoArgsConstructor
@Slf4j
public class BusinessRuleTaskInstanceProcessor
    extends ActivityInstanceProcessor<
        BusinessRuleTask, BusinessRuleTaskInstance, ContinueFlowElementTriggerDTO> {

  private DmnEvaluator dmnEvaluator;
  private DmnDecisionResolver dmnDecisionResolver;

  @Inject
  public BusinessRuleTaskInstanceProcessor(
      FeelExpressionHandler feelExpressionHandler,
      IoMappingProcessor ioMappingProcessor,
      ProcessInstanceMapper processInstanceMapper,
      Clock clock,
      DmnEvaluator dmnEvaluator,
      DmnDecisionResolver dmnDecisionResolver) {
    super(feelExpressionHandler, ioMappingProcessor, processInstanceMapper, clock);
    this.dmnEvaluator = dmnEvaluator;
    this.dmnDecisionResolver = dmnDecisionResolver;
  }

  @Override
  protected void processStartSpecificActivityInstance(
      ProcessInstanceProcessingContext processInstanceProcessingContext,
      Scope scope,
      VariableScope variableScope,
      BusinessRuleTaskInstance flownodeInstance,
      String inputFlowId) {
    BusinessRuleTask task = flownodeInstance.getFlowNode();
    String decisionId = task.getDecisionId();
    String resultVariable = task.getResultVariable();

    log.debug("Evaluating DMN decision '{}' for task '{}'", decisionId, task.getId());

    DmnDecisionDTO decision =
        dmnDecisionResolver
            .resolve(decisionId)
            .orElseThrow(
                () ->
                    new ProcessInstanceException(
                        flownodeInstance,
                        "DMN decision '" + decisionId + "' not found in any deployed DMN"));

    JsonNode result = dmnEvaluator.evaluate(decision, variableScope);

    if (resultVariable != null && !resultVariable.isBlank()) {
      variableScope.put(resultVariable, result);
    }
    flownodeInstance.setState(ExecutionState.COMPLETED);
  }

  @Override
  protected void processContinueSpecificActivityInstance(
      ProcessInstanceProcessingContext processInstanceProcessingContext,
      Scope scope,
      VariableScope variableScope,
      BusinessRuleTaskInstance externalTaskInstance,
      ContinueFlowElementTriggerDTO trigger) {
    // Business rule tasks complete synchronously; no continuation expected
  }

  @Override
  protected void processAbortSpecificActivityInstance(
      ProcessInstanceProcessingContext processInstanceProcessingContext,
      Scope scope,
      VariableScope variableScope,
      BusinessRuleTaskInstance instance) {
    // Nothing to do
  }
}
