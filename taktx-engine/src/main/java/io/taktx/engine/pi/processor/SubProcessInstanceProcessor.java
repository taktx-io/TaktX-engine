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
import io.taktx.dto.v_1_0_0.FlowNodeInstanceDTO;
import io.taktx.dto.v_1_0_0.FlowNodeInstanceKeyDTO;
import io.taktx.dto.v_1_0_0.TerminateTriggerDTO;
import io.taktx.engine.feel.FeelExpressionHandler;
import io.taktx.engine.pd.model.EventSignal;
import io.taktx.engine.pd.model.FlowElements;
import io.taktx.engine.pd.model.SubProcess;
import io.taktx.engine.pi.DirectInstanceResult;
import io.taktx.engine.pi.FlowNodeInstancesProcessor;
import io.taktx.engine.pi.InstanceResult;
import io.taktx.engine.pi.ProcessInstanceMapper;
import io.taktx.engine.pi.ProcessingStatistics;
import io.taktx.engine.pi.model.FlowNodeInstances;
import io.taktx.engine.pi.model.ProcessInstance;
import io.taktx.engine.pi.model.SubProcessInstance;
import io.taktx.engine.pi.model.VariableScope;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.Clock;
import java.util.List;
import java.util.Queue;
import lombok.NoArgsConstructor;
import org.apache.kafka.streams.state.KeyValueStore;

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
      KeyValueStore<FlowNodeInstanceKeyDTO, FlowNodeInstanceDTO> flowNodeInstanceStore,
      InstanceResult instanceResult,
      DirectInstanceResult directInstanceResult,
      FlowElements flowElements,
      SubProcessInstance subProcessInstance,
      ProcessInstance processInstance,
      String inputFlowId,
      VariableScope flowNodeInstanceVariables,
      ProcessingStatistics processingStatistics) {

    FlowNodeInstances subFlowNodeInstances = new FlowNodeInstances();
    subFlowNodeInstances.setParentFlowNodeInstance(subProcessInstance);
    subProcessInstance.setFlowNodeInstances(subFlowNodeInstances);
    subProcessInstance.setState(ActtivityStateEnum.WAITING);

    FlowElements subProcessElements = subProcessInstance.getFlowNode().getElements();
    subProcessElements.getIndex().addAll(flowElements.getIndex());

    flowNodeInstancesProcessor.processStart(
        flowNodeInstanceStore,
        instanceResult,
        null,
        subProcessInstance,
        subProcessElements,
        processInstance,
        flowNodeInstanceVariables,
        subFlowNodeInstances,
        processingStatistics);

    if (subFlowNodeInstances.getState().isFinished()) {
      subProcessInstance.setState(ActtivityStateEnum.FINISHED);
    }
  }

  @Override
  protected void processContinueSpecificActivityInstance(
      KeyValueStore<FlowNodeInstanceKeyDTO, FlowNodeInstanceDTO> flowNodeInstanceStore,
      InstanceResult instanceResult,
      DirectInstanceResult directInstanceResult,
      int subProcessLevel,
      FlowElements flowElements,
      ProcessInstance processInstance,
      SubProcessInstance subProcessInstance,
      ContinueFlowElementTriggerDTO trigger,
      VariableScope flowNodeInstanceVariables,
      ProcessingStatistics processingStatistics) {
    subProcessLevel++;

    FlowElements subProcessElements = subProcessInstance.getFlowNode().getElements();
    subProcessInstance.getFlowNodeInstances().setParentFlowNodeInstance(subProcessInstance);

    flowNodeInstancesProcessor.processContinue(
        flowNodeInstanceStore,
        instanceResult,
        subProcessLevel,
        trigger,
        subProcessElements,
        processInstance,
        flowNodeInstanceVariables,
        subProcessInstance.getFlowNodeInstances(),
        processingStatistics);

    Queue<EventSignal> bubbleUpEvents = instanceResult.getBubbleUpEvents();
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
      KeyValueStore<FlowNodeInstanceKeyDTO, FlowNodeInstanceDTO> flowNodeInstanceStore,
      InstanceResult instanceResult,
      DirectInstanceResult directInstanceResult,
      SubProcessInstance subProcessInstance,
      ProcessInstance processInstance,
      VariableScope flowNodeInstanceVariables,
      ProcessingStatistics processingStatistics) {

    // Terminate all childelements
    FlowNodeInstances flowNodeInstances = subProcessInstance.getFlowNodeInstances();
    flowNodeInstances.setParentFlowNodeInstance(subProcessInstance);

    TerminateTriggerDTO trigger =
        new TerminateTriggerDTO(processInstance.getProcessInstanceKey(), List.of());
    flowNodeInstancesProcessor.processTerminate(
        flowNodeInstanceStore,
        instanceResult,
        trigger,
        processInstance,
        flowNodeInstances,
        flowNodeInstanceVariables,
        subProcessInstance.getFlowNode().getElements(),
        processingStatistics);
  }
}
