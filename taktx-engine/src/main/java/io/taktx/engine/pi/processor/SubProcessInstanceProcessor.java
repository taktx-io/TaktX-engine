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

import io.taktx.dto.v_1_0_0.ActtivityStateEnum;
import io.taktx.dto.v_1_0_0.ContinueFlowElementTriggerDTO;
import io.taktx.dto.v_1_0_0.TerminateTriggerDTO;
import io.taktx.engine.feel.FeelExpressionHandler;
import io.taktx.engine.pd.model.EventSignal;
import io.taktx.engine.pd.model.FlowElements;
import io.taktx.engine.pd.model.SubProcess;
import io.taktx.engine.pi.DirectInstanceResult;
import io.taktx.engine.pi.FlowNodeInstancesProcessor;
import io.taktx.engine.pi.ProcessInstanceMapper;
import io.taktx.engine.pi.ProcessingContext;
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
      ProcessingContext processingContext,
      DirectInstanceResult directInstanceResult,
      FlowElements flowElements,
      SubProcessInstance subProcessInstance,
      String inputFlowId,
      VariableScope flowNodeInstanceVariables) {

    FlowNodeInstances subFlowNodeInstances = new FlowNodeInstances();
    subFlowNodeInstances.setParentFlowNodeInstance(subProcessInstance);
    subProcessInstance.setFlowNodeInstances(subFlowNodeInstances);
    subProcessInstance.setState(ActtivityStateEnum.WAITING);

    FlowElements subProcessElements = subProcessInstance.getFlowNode().getElements();
    subProcessElements.getIndex().addAll(flowElements.getIndex());

    flowNodeInstancesProcessor.processStart(
        processingContext,
        null,
        subProcessInstance,
        subProcessElements,
        flowNodeInstanceVariables,
        subFlowNodeInstances);

    if (subFlowNodeInstances.getState().isFinished()) {
      subProcessInstance.setState(ActtivityStateEnum.FINISHED);
    }
  }

  @Override
  protected void processContinueSpecificActivityInstance(
      ProcessingContext processingContext,
      DirectInstanceResult directInstanceResult,
      int subProcessLevel,
      FlowElements flowElements,
      SubProcessInstance subProcessInstance,
      ContinueFlowElementTriggerDTO trigger,
      VariableScope flowNodeInstanceVariables) {
    subProcessLevel++;

    FlowElements subProcessElements = subProcessInstance.getFlowNode().getElements();
    subProcessInstance.getFlowNodeInstances().setParentFlowNodeInstance(subProcessInstance);

    flowNodeInstancesProcessor.processContinue(
        processingContext,
        subProcessLevel,
        trigger,
        subProcessElements,
        flowNodeInstanceVariables,
        subProcessInstance.getFlowNodeInstances());

    Queue<EventSignal> bubbleUpEvents = processingContext.getInstanceResult().getBubbleUpEvents();
    EventSignal eventSignal = bubbleUpEvents.poll();
    while (eventSignal != null) {
      eventSignal.bubbleUp();
      directInstanceResult.addEvent(eventSignal);
      eventSignal = bubbleUpEvents.poll();
    }

    if (subProcessInstance.getFlowNodeInstances().getState().isFinished()) {
      subProcessInstance.setState(ActtivityStateEnum.FINISHED);
    }
  }

  @Override
  protected void processTerminateSpecificActivityInstance(
      ProcessingContext processingContext,
      DirectInstanceResult directInstanceResult,
      SubProcessInstance subProcessInstance,
      VariableScope flowNodeInstanceVariables) {

    // Terminate all childelements
    FlowNodeInstances flowNodeInstances = subProcessInstance.getFlowNodeInstances();
    flowNodeInstances.setParentFlowNodeInstance(subProcessInstance);

    TerminateTriggerDTO trigger =
        new TerminateTriggerDTO(
            processingContext.getProcessInstance().getProcessInstanceKey(), List.of());
    flowNodeInstancesProcessor.processTerminate(
        processingContext,
        trigger,
        flowNodeInstances,
        flowNodeInstanceVariables,
        subProcessInstance.getFlowNode().getElements());
  }
}
