/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks All rights reserved.
 * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 * Free use is permitted with up to 3 Kafka partitions per topic. See LICENSE file for details.
 * For commercial use or more partitions and features, contact [https://www.taktx.io/contact].
 */

package io.taktx.engine.pi.processor;

import io.taktx.dto.ActtivityStateEnum;
import io.taktx.dto.ContinueFlowElementTriggerDTO;
import io.taktx.dto.TerminateTriggerDTO;
import io.taktx.engine.feel.FeelExpressionHandler;
import io.taktx.engine.pd.model.EventSignal;
import io.taktx.engine.pd.model.FlowElements;
import io.taktx.engine.pd.model.SubProcess;
import io.taktx.engine.pi.FlowNodeInstanceProcessingContext;
import io.taktx.engine.pi.FlowNodeInstancesProcessor;
import io.taktx.engine.pi.ProcessInstanceMapper;
import io.taktx.engine.pi.ProcessInstanceProcessingContext;
import io.taktx.engine.pi.model.FlowNodeInstances;
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

  private FlowNodeInstancesProcessor flowNodeInstancesProcessor;

  @Inject
  public SubProcessInstanceProcessor(
      FeelExpressionHandler feelExpressionHandler,
      IoMappingProcessor ioMappingProcessor,
      FlowNodeInstancesProcessor flowNodeInstancesProcessor,
      ProcessInstanceMapper processInstanceMapper,
      Clock clock) {
    super(feelExpressionHandler, ioMappingProcessor, processInstanceMapper, clock);
    this.flowNodeInstancesProcessor = flowNodeInstancesProcessor;
  }

  @Override
  protected void processStartSpecificActivityInstance(
      ProcessInstanceProcessingContext processInstanceProcessingContext,
      FlowNodeInstanceProcessingContext flowNodeInstanceProcessingContext,
      SubProcessInstance subProcessInstance,
      String inputFlowId,
      VariableScope flowNodeInstanceVariables) {

    FlowNodeInstances subFlowNodeInstances = new FlowNodeInstances();
    subFlowNodeInstances.setParentFlowNodeInstance(subProcessInstance);
    subProcessInstance.setFlowNodeInstances(subFlowNodeInstances);
    subProcessInstance.setState(ActtivityStateEnum.WAITING);

    FlowElements subProcessElements = subProcessInstance.getFlowNode().getElements();
    subProcessElements
        .getIndex()
        .addAll(flowNodeInstanceProcessingContext.getFlowElements().getIndex());
    FlowNodeInstanceProcessingContext subFlowNodeInstanceProcessingContext =
        new FlowNodeInstanceProcessingContext(
            subFlowNodeInstances,
            flowNodeInstanceProcessingContext.getSubProcessLevel() + 1,
            subProcessElements);
    flowNodeInstancesProcessor.processStart(
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
    if (subFlowNodeInstances.getState().isFinished()) {
      subProcessInstance.setState(ActtivityStateEnum.FINISHED);
    }
  }

  @Override
  protected void processContinueSpecificActivityInstance(
      ProcessInstanceProcessingContext processInstanceProcessingContext,
      FlowNodeInstanceProcessingContext flowNodeInstanceProcessingContext,
      SubProcessInstance subProcessInstance,
      ContinueFlowElementTriggerDTO trigger,
      VariableScope flowNodeInstanceVariables) {

    FlowElements subProcessElements = subProcessInstance.getFlowNode().getElements();
    subProcessInstance.getFlowNodeInstances().setParentFlowNodeInstance(subProcessInstance);
    FlowNodeInstanceProcessingContext subFlowNodeContext =
        new FlowNodeInstanceProcessingContext(
            subProcessInstance.getFlowNodeInstances(),
            flowNodeInstanceProcessingContext.getSubProcessLevel() + 1,
            subProcessElements);
    flowNodeInstancesProcessor.processContinue(
        processInstanceProcessingContext, subFlowNodeContext, trigger, flowNodeInstanceVariables);

    Queue<EventSignal> bubbleUpEvents =
        processInstanceProcessingContext.getInstanceResult().getBubbleUpEvents();
    EventSignal eventSignal = bubbleUpEvents.poll();
    while (eventSignal != null) {
      eventSignal.bubbleUp();
      flowNodeInstanceProcessingContext.getDirectInstanceResult().addEvent(eventSignal);
      eventSignal = bubbleUpEvents.poll();
    }

    if (subProcessInstance.getFlowNodeInstances().getState().isFinished()) {
      subProcessInstance.setState(ActtivityStateEnum.FINISHED);
    }
  }

  @Override
  protected void processTerminateSpecificActivityInstance(
      ProcessInstanceProcessingContext processInstanceProcessingContext,
      FlowNodeInstanceProcessingContext flowNodeInstanceProcessingContext,
      SubProcessInstance subProcessInstance,
      VariableScope flowNodeInstanceVariables) {

    FlowNodeInstances flowNodeInstances = subProcessInstance.getFlowNodeInstances();
    flowNodeInstances.setParentFlowNodeInstance(subProcessInstance);

    // Terminate all childelements
    FlowNodeInstanceProcessingContext subFlowNodeInstanceProcessingContext =
        new FlowNodeInstanceProcessingContext(
            flowNodeInstances,
            flowNodeInstanceProcessingContext.getSubProcessLevel() + 1,
            subProcessInstance.getFlowNode().getElements());

    TerminateTriggerDTO trigger =
        new TerminateTriggerDTO(
            processInstanceProcessingContext.getProcessInstance().getProcessInstanceKey(),
            List.of());
    flowNodeInstancesProcessor.processTerminate(
        processInstanceProcessingContext,
        subFlowNodeInstanceProcessingContext,
        trigger,
        flowNodeInstanceVariables);
  }
}
