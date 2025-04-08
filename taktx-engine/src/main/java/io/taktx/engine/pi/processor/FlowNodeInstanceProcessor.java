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

import io.taktx.dto.v_1_0_0.ContinueFlowElementTriggerDTO;
import io.taktx.dto.v_1_0_0.FlowNodeInstanceDTO;
import io.taktx.dto.v_1_0_0.FlowNodeInstanceUpdateDTO;
import io.taktx.dto.v_1_0_0.VariablesDTO;
import io.taktx.engine.pd.model.FlowElements;
import io.taktx.engine.pd.model.FlowNode;
import io.taktx.engine.pd.model.SequenceFlow;
import io.taktx.engine.pd.model.WithIoMapping;
import io.taktx.engine.pi.DirectInstanceResult;
import io.taktx.engine.pi.InstanceUpdate;
import io.taktx.engine.pi.ProcessInstanceMapper;
import io.taktx.engine.pi.ProcessingContext;
import io.taktx.engine.pi.model.FlowNodeInstance;
import io.taktx.engine.pi.model.FlowNodeInstanceInfo;
import io.taktx.engine.pi.model.FlowNodeInstances;
import io.taktx.engine.pi.model.ProcessInstance;
import io.taktx.engine.pi.model.VariableScope;
import java.time.Clock;
import java.util.List;
import java.util.Set;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public abstract class FlowNodeInstanceProcessor<
    E extends FlowNode, I extends FlowNodeInstance<?>, C extends ContinueFlowElementTriggerDTO> {
  protected IoMappingProcessor ioMappingProcessor;
  protected Clock clock;
  protected ProcessInstanceMapper processInstanceMapper;

  protected FlowNodeInstanceProcessor(
      IoMappingProcessor ioMappingProcessor,
      ProcessInstanceMapper processInstanceMapper,
      Clock clock) {
    this.ioMappingProcessor = ioMappingProcessor;
    this.processInstanceMapper = processInstanceMapper;
    this.clock = clock;
  }

  public void processStart(
      ProcessingContext processingContext,
      DirectInstanceResult directInstanceResult,
      FlowElements flowElements,
      FlowNodeInstance<?> flownodeInstance,
      String inputFlowId,
      VariableScope parentVariableScope,
      FlowNodeInstances flowNodeInstances) {

    VariableScope currentVariableScope =
        parentVariableScope.selectFlowNodeInstancesScope(flownodeInstance.getElementInstanceId());

    if (!flownodeInstance.stateAllowsStart()) {
      return;
    }

    flownodeInstance.setStartedState();

    processingContext.getProcessingStatistics().increaseFlowNodesStarted();

    long now = clock.instant().toEpochMilli();

    E flowNode = (E) flownodeInstance.getFlowNode();

    addInputVariablesToScope(flowNode, currentVariableScope);

    this.processStartSpecificFlowNodeInstance(
        processingContext,
        flowNodeInstances,
        directInstanceResult,
        flowElements,
        (I) flownodeInstance,
        inputFlowId,
        currentVariableScope);

    if (flownodeInstance.isCompleted()) {
      processingContext.getProcessingStatistics().increaseFlowNodesFinished();
    }

    ProcessInstance processInstance = processingContext.getProcessInstance();
    selectNextNodeIfAllowedStart(
        processInstance,
        (I) flownodeInstance,
        directInstanceResult,
        currentVariableScope,
        flowNodeInstances);

    processingContext
        .getInstanceResult()
        .addInstanceUpdate(
            createFlowNodeInstanceUpdate(
                processInstance, flownodeInstance, currentVariableScope, now, flowElements));
  }

  public final void processContinue(
      ProcessingContext processingContext,
      DirectInstanceResult directInstanceResult,
      int subProcessLevel,
      FlowElements flowElements,
      FlowNodeInstance<?> flowNodeInstance,
      ContinueFlowElementTriggerDTO trigger,
      VariableScope parentVariableScope,
      FlowNodeInstances flowNodeInstances) {
    if (!flowNodeInstance.stateAllowsContinue()) {
      return;
    }

    VariableScope currentVariableScope =
        parentVariableScope.selectFlowNodeInstancesScope(flowNodeInstance.getElementInstanceId());

    processingContext.getProcessingStatistics().increaseFlowNodesContinued();

    long now = clock.instant().toEpochMilli();

    this.processContinueSpecificFlowNodeInstance(
        processingContext,
        directInstanceResult,
        subProcessLevel,
        flowElements,
        (I) flowNodeInstance,
        (C) trigger,
        currentVariableScope,
        flowNodeInstances);

    if (flowNodeInstance.isCompleted()) {
      processingContext.getProcessingStatistics().increaseFlowNodesFinished();
    }

    ProcessInstance processInstance = processingContext.getProcessInstance();
    selectNextNodeIfAllowedContinue(
        (I) flowNodeInstance,
        processInstance,
        directInstanceResult,
        currentVariableScope,
        flowNodeInstances);

    processingContext
        .getInstanceResult()
        .addInstanceUpdate(
            createFlowNodeInstanceUpdate(
                processInstance, flowNodeInstance, currentVariableScope, now, flowElements));
  }

  public void processTerminate(
      ProcessingContext processingContext,
      DirectInstanceResult directInstanceResult,
      FlowNodeInstance<?> instance,
      VariableScope parentVariablesScope,
      FlowNodeInstances flowNodeInstances,
      FlowElements flowElements) {
    // Only terminate if the instance is ready or waiting
    if (instance.stateAllowsTerminate()) {
      long now = clock.instant().toEpochMilli();

      VariableScope currentVariableScope =
          parentVariablesScope.selectFlowNodeInstancesScope(instance.getElementInstanceId());
      ProcessInstance processInstance = processingContext.getProcessInstance();
      processTerminateSpecificFlowNodeInstance(
          processingContext,
          directInstanceResult,
          (I) instance,
          currentVariableScope,
          flowElements);

      instance.terminate();

      processingContext.getProcessingStatistics().increaseFlowNodesFinished();

      processingContext
          .getInstanceResult()
          .addInstanceUpdate(
              createFlowNodeInstanceUpdate(
                  processInstance, instance, currentVariableScope, now, flowElements));
    }
  }

  protected void addInputVariablesToScope(E flowNode, VariableScope flowNodeInstanceVariables) {
    if (flowNode instanceof WithIoMapping withIoMapping) {
      ioMappingProcessor.addInputVariables(withIoMapping, flowNodeInstanceVariables);
    }
  }

  protected void selectNextNodeIfAllowedStart(
      ProcessInstance processInstance,
      I flownodeInstance,
      DirectInstanceResult directInstanceResult,
      VariableScope processInstanceVariables,
      FlowNodeInstances flowNodeInstances) {
    if (flownodeInstance.canSelectNextNodeStart()) {

      processNodeResultAndSelectNextInstance(
          processInstance,
          flownodeInstance,
          directInstanceResult,
          processInstanceVariables,
          flowNodeInstances);
    }
  }

  protected void selectNextNodeIfAllowedContinue(
      I flownodeInstance,
      ProcessInstance processInstance,
      DirectInstanceResult directInstanceResult,
      VariableScope currentVariableScope,
      FlowNodeInstances flowNodeInstances) {
    if (flownodeInstance.canSelectNextNodeContinue()) {

      processNodeResultAndSelectNextInstance(
          processInstance,
          flownodeInstance,
          directInstanceResult,
          currentVariableScope,
          flowNodeInstances);
    }
  }

  protected void processNodeResultAndSelectNextInstance(
      ProcessInstance processInstance,
      I flownodeInstance,
      DirectInstanceResult directInstanceResult,
      VariableScope currentVariableScope,
      FlowNodeInstances flowNodeInstances) {
    FlowNode flowNode = flownodeInstance.getFlowNode();
    if (flowNode instanceof WithIoMapping withIoMapping) {
      ioMappingProcessor.processOutputMappings(withIoMapping, currentVariableScope);
    }

    flownodeInstance.increasePassedCnt();
    getSelectedSequenceFlows(
            processInstance, flownodeInstance, flowNodeInstances, currentVariableScope)
        .forEach(
            sequenceFlow -> {
              FlowNodeInstance<?> newFlowNodeInstance =
                  sequenceFlow
                      .getTargetNode()
                      .createAndStoreNewInstance(
                          flownodeInstance.getParentInstance(), flowNodeInstances);
              directInstanceResult.addNewFlowNodeInstance(
                  processInstance,
                  new FlowNodeInstanceInfo(newFlowNodeInstance, sequenceFlow.getId()));
            });
  }

  protected abstract Set<SequenceFlow> getSelectedSequenceFlows(
      ProcessInstance processInstance,
      I flowNodeInstance,
      FlowNodeInstances flowNodeInstances,
      VariableScope variables);

  protected abstract void processStartSpecificFlowNodeInstance(
      ProcessingContext processingContext,
      FlowNodeInstances flowNodeInstances,
      DirectInstanceResult directInstanceResult,
      FlowElements flowElements,
      I flownodeInstance,
      String inputFlowId,
      VariableScope variables);

  protected abstract void processContinueSpecificFlowNodeInstance(
      ProcessingContext processingContext,
      DirectInstanceResult directInstanceResult,
      int subProcessLevel,
      FlowElements flowElements,
      I flowNodeInstance,
      C trigger,
      VariableScope variables,
      FlowNodeInstances flowNodeInstances);

  protected abstract void processTerminateSpecificFlowNodeInstance(
      ProcessingContext processingContext,
      DirectInstanceResult directInstanceResult,
      I instance,
      VariableScope currentVariableScope,
      FlowElements flowElements);

  protected InstanceUpdate createFlowNodeInstanceUpdate(
      ProcessInstance processInstance,
      FlowNodeInstance<?> flowNodeInstance,
      VariableScope variables,
      long processTime,
      FlowElements flowElements) {
    List<Long> elementInstanceIdPath = flowNodeInstance.createKeyPath();
    VariablesDTO processInstanceVariablesDTO = variables.scopeToDTO();
    FlowNodeInstanceDTO flowNodeInstanceDTO =
        processInstanceMapper.map(flowNodeInstance, flowElements);
    return new InstanceUpdate(
        processInstance.getProcessInstanceKey(),
        new FlowNodeInstanceUpdateDTO(
            elementInstanceIdPath, flowNodeInstanceDTO, processInstanceVariablesDTO, processTime));
  }
}
