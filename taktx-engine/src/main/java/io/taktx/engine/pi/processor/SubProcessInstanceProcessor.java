/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks All rights reserved.
 * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 * Free use is permitted with up to 3 Kafka partitions per topic. See LICENSE file for details.
 * For commercial use or more partitions and features, contact [https://www.taktx.io/contact].
 */

package io.taktx.engine.pi.processor;

import io.taktx.dto.AbortTriggerDTO;
import io.taktx.dto.ContinueFlowElementTriggerDTO;
import io.taktx.dto.ExecutionState;
import io.taktx.engine.feel.FeelExpressionHandler;
import io.taktx.engine.pd.model.FlowElements;
import io.taktx.engine.pd.model.SubProcess;
import io.taktx.engine.pi.PathExtractor;
import io.taktx.engine.pi.ProcessInstanceMapper;
import io.taktx.engine.pi.ProcessInstanceProcessingContext;
import io.taktx.engine.pi.ScopeProcessor;
import io.taktx.engine.pi.model.Scope;
import io.taktx.engine.pi.model.SubProcessInstance;
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
      SubProcessInstance subProcessInstance,
      String inputFlowId) {

    Scope subScope = scope.selectChildScope(subProcessInstance);
    subProcessInstance.setScope(subScope);
    subProcessInstance.setState(ExecutionState.ACTIVE);

    FlowElements subProcessElements = subProcessInstance.getFlowNode().getElements();
    subProcessElements.getIndex().addAll(scope.getFlowElements().getIndex());

    List<Long> instancePath = pathExtractor.getInstancePath(subProcessInstance);
    scopeProcessor.processStart(instancePath, null, processInstanceProcessingContext, subScope);

    subProcessInstance.setState(subScope.getState());
  }

  @Override
  protected void processContinueSpecificActivityInstance(
      ProcessInstanceProcessingContext processInstanceProcessingContext,
      Scope scope,
      SubProcessInstance subProcessInstance,
      ContinueFlowElementTriggerDTO trigger) {
    throw new IllegalStateException("We should never continue a subprocess");
  }

  @Override
  protected void processTerminateSpecificActivityInstance(
      ProcessInstanceProcessingContext processInstanceProcessingContext,
      Scope scope,
      SubProcessInstance subProcessInstance) {

    Scope childScope = subProcessInstance.getScope();

    AbortTriggerDTO trigger =
        new AbortTriggerDTO(
            processInstanceProcessingContext.getProcessInstance().getProcessInstanceId(),
            List.of());
    scopeProcessor.processTerminate(processInstanceProcessingContext, childScope, trigger);
  }
}
