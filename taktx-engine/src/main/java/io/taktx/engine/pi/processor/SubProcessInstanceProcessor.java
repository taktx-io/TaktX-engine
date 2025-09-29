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
import io.taktx.engine.pd.model.EventSignal;
import io.taktx.engine.pd.model.FlowElements;
import io.taktx.engine.pd.model.SubProcess;
import io.taktx.engine.pi.FlowNodeInstanceProcessingContext;
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
import java.util.Queue;
import lombok.NoArgsConstructor;

@ApplicationScoped
@NoArgsConstructor
public class SubProcessInstanceProcessor
    extends ActivityInstanceProcessor<
        SubProcess, SubProcessInstance, ContinueFlowElementTriggerDTO> {

  private ScopeProcessor scopeProcessor;

  @Inject
  public SubProcessInstanceProcessor(
      FeelExpressionHandler feelExpressionHandler,
      IoMappingProcessor ioMappingProcessor,
      ScopeProcessor scopeProcessor,
      ProcessInstanceMapper processInstanceMapper,
      Clock clock) {
    super(feelExpressionHandler, ioMappingProcessor, processInstanceMapper, clock);
    this.scopeProcessor = scopeProcessor;
  }

  @Override
  protected void processStartSpecificActivityInstance(
      ProcessInstanceProcessingContext processInstanceProcessingContext,
      FlowNodeInstanceProcessingContext flowNodeInstanceProcessingContext,
      SubProcessInstance subProcessInstance,
      String inputFlowId,
      VariableScope flowNodeInstanceVariables) {

    Scope subScope = new Scope();
    subScope.setParentFlowNodeInstance(subProcessInstance);
    subProcessInstance.setScope(subScope);
    subProcessInstance.setState(ExecutionState.ACTIVE);

    FlowElements subProcessElements = subProcessInstance.getFlowNode().getElements();
    subProcessElements
        .getIndex()
        .addAll(flowNodeInstanceProcessingContext.getFlowElements().getIndex());
    FlowNodeInstanceProcessingContext subFlowNodeInstanceProcessingContext =
        new FlowNodeInstanceProcessingContext(
            subScope,
            flowNodeInstanceProcessingContext.getSubProcessLevel() + 1,
            subProcessElements);

    scopeProcessor.processStart(
        processInstanceProcessingContext,
        subFlowNodeInstanceProcessingContext,
        null,
        subProcessInstance,
        flowNodeInstanceVariables);

    flowNodeInstanceProcessingContext
        .getDirectInstanceResult()
        .setTerminateParentPath(
            subFlowNodeInstanceProcessingContext
                .getDirectInstanceResult()
                .getTerminateParentPath());

    subProcessInstance.setState(subScope.getState());
  }

  @Override
  protected void processContinueSpecificActivityInstance(
      ProcessInstanceProcessingContext processInstanceProcessingContext,
      FlowNodeInstanceProcessingContext flowNodeInstanceProcessingContext,
      SubProcessInstance subProcessInstance,
      ContinueFlowElementTriggerDTO trigger,
      VariableScope flowNodeInstanceVariables) {

    FlowElements subProcessElements = subProcessInstance.getFlowNode().getElements();
    subProcessInstance.getScope().setParentFlowNodeInstance(subProcessInstance);
    FlowNodeInstanceProcessingContext subFlowNodeContext =
        new FlowNodeInstanceProcessingContext(
            subProcessInstance.getScope(),
            flowNodeInstanceProcessingContext.getSubProcessLevel() + 1,
            subProcessElements);

    scopeProcessor.processContinue(
        processInstanceProcessingContext, subFlowNodeContext, trigger, flowNodeInstanceVariables);

    Queue<EventSignal> bubbleUpEvents =
        processInstanceProcessingContext.getInstanceResult().getBubbleUpEvents();
    EventSignal eventSignal = bubbleUpEvents.poll();
    while (eventSignal != null) {
      eventSignal.bubbleUp();
      flowNodeInstanceProcessingContext.getDirectInstanceResult().addEvent(eventSignal);
      eventSignal = bubbleUpEvents.poll();
    }

    subProcessInstance.setState(subProcessInstance.getScope().getState());
  }

  @Override
  protected void processTerminateSpecificActivityInstance(
      ProcessInstanceProcessingContext processInstanceProcessingContext,
      FlowNodeInstanceProcessingContext flowNodeInstanceProcessingContext,
      SubProcessInstance subProcessInstance,
      VariableScope flowNodeInstanceVariables) {

    Scope scope = subProcessInstance.getScope();
    scope.setParentFlowNodeInstance(subProcessInstance);

    // Terminate all childelements
    FlowNodeInstanceProcessingContext subFlowNodeInstanceProcessingContext =
        new FlowNodeInstanceProcessingContext(
            scope,
            flowNodeInstanceProcessingContext.getSubProcessLevel() + 1,
            subProcessInstance.getFlowNode().getElements());

    AbortTriggerDTO trigger =
        new AbortTriggerDTO(
            processInstanceProcessingContext.getProcessInstance().getProcessInstanceId(),
            List.of());
    scopeProcessor.processTerminate(
        processInstanceProcessingContext,
        subFlowNodeInstanceProcessingContext,
        trigger,
        flowNodeInstanceVariables);
  }
}
