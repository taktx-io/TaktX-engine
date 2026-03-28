/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */

package io.taktx.engine.pi.processor;

import io.taktx.dto.AbortTriggerDTO;
import io.taktx.dto.ContinueFlowElementTriggerDTO;
import io.taktx.dto.ExecutionState;
import io.taktx.dto.VariablesDTO;
import io.taktx.engine.feel.FeelExpressionHandler;
import io.taktx.engine.pd.model.FlowElements;
import io.taktx.engine.pd.model.SubProcess;
import io.taktx.engine.pi.PathExtractor;
import io.taktx.engine.pi.ProcessInstanceMapper;
import io.taktx.engine.pi.ProcessInstanceProcessingContext;
import io.taktx.engine.pi.ScopeProcessor;
import io.taktx.engine.pi.model.Scope;
import io.taktx.engine.pi.model.SubProcessInstance;
import io.taktx.engine.pi.model.VariableScope;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.Clock;
import java.util.List;
import lombok.NoArgsConstructor;

@ApplicationScoped
@NoArgsConstructor
public class SubProcessInstanceProcessor
    extends ActivityInstanceProcessor<
        SubProcess, SubProcessInstance, ContinueFlowElementTriggerDTO> {

  private ScopeProcessor scopeProcessor;
  private PathExtractor pathExtractor;

  @Inject
  public SubProcessInstanceProcessor(
      FeelExpressionHandler feelExpressionHandler,
      IoMappingProcessor ioMappingProcessor,
      ScopeProcessor scopeProcessor,
      ProcessInstanceMapper processInstanceMapper,
      Clock clock,
      PathExtractor pathExtractor) {
    super(feelExpressionHandler, ioMappingProcessor, processInstanceMapper, clock);
    this.scopeProcessor = scopeProcessor;
    this.pathExtractor = pathExtractor;
  }

  @Override
  protected void processStartSpecificActivityInstance(
      ProcessInstanceProcessingContext processInstanceProcessingContext,
      Scope scope,
      VariableScope variableScope,
      SubProcessInstance subProcessInstance,
      String inputFlowId) {

    Scope subScope =
        scope.selectChildScope(subProcessInstance, subProcessInstance.getFlowElements());
    subProcessInstance.setScope(subScope);
    subProcessInstance.setState(ExecutionState.ACTIVE);

    FlowElements subProcessElements = subProcessInstance.getFlowNode().getElements();
    subProcessElements.getIndex().addAll(scope.getFlowElements().getIndex());

    List<Long> instancePath = pathExtractor.getInstancePath(subProcessInstance);

    scopeProcessor.processStart(
        instancePath,
        null,
        VariablesDTO.empty(),
        processInstanceProcessingContext,
        subScope,
        variableScope);

    scopeProcessor.bubbleUpEvents(scope, subProcessInstance);

    subProcessInstance.setState(subScope.getState());
  }

  @Override
  protected void processContinueSpecificActivityInstance(
      ProcessInstanceProcessingContext processInstanceProcessingContext,
      Scope scope,
      VariableScope variableScope,
      SubProcessInstance subProcessInstance,
      ContinueFlowElementTriggerDTO trigger) {
    subProcessInstance.setState(subProcessInstance.getScope().getState());
  }

  @Override
  protected void processAbortSpecificActivityInstance(
      ProcessInstanceProcessingContext processInstanceProcessingContext,
      Scope scope,
      VariableScope variableScope,
      SubProcessInstance subProcessInstance) {

    Scope childScope = subProcessInstance.getScope();

    AbortTriggerDTO trigger =
        new AbortTriggerDTO(
            processInstanceProcessingContext.getProcessInstance().getProcessInstanceId(),
            List.of());
    scopeProcessor.processAbort(
        processInstanceProcessingContext, childScope, variableScope, trigger);
  }
}
