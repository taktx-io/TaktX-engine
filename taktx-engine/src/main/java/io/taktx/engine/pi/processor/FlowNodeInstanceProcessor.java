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

import io.taktx.dto.ContinueFlowElementTriggerDTO;
import io.taktx.dto.FlowNodeInstanceDTO;
import io.taktx.dto.FlowNodeInstanceUpdateDTO;
import io.taktx.dto.VariablesDTO;
import io.taktx.engine.pd.model.FlowElements;
import io.taktx.engine.pd.model.FlowNode;
import io.taktx.engine.pd.model.SequenceFlow;
import io.taktx.engine.pd.model.WithIoMapping;
import io.taktx.engine.pi.FlowNodeInstanceProcessingContext;
import io.taktx.engine.pi.InstanceUpdate;
import io.taktx.engine.pi.ProcessInstanceMapper;
import io.taktx.engine.pi.ProcessInstanceProcessingContext;
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
      ProcessInstanceProcessingContext processInstanceProcessingContext,
      FlowNodeInstanceProcessingContext flowNodeInstanceProcessingContext,
      FlowNodeInstance<?> flownodeInstance,
      String inputFlowId,
      VariableScope parentVariableScope) {

    VariableScope currentVariableScope =
        parentVariableScope.selectFlowNodeInstancesScope(flownodeInstance.getElementInstanceId());

    if (!flownodeInstance.stateAllowsStart()) {
      return;
    }

    flownodeInstance.setStartedState();

    processInstanceProcessingContext.getProcessingStatistics().increaseFlowNodesStarted();

    long now = clock.instant().toEpochMilli();

    E flowNode = (E) flownodeInstance.getFlowNode();

    addInputVariablesToScope(flowNode, currentVariableScope);

    this.processStartSpecificFlowNodeInstance(
        processInstanceProcessingContext,
        flowNodeInstanceProcessingContext,
        (I) flownodeInstance,
        inputFlowId,
        currentVariableScope);

    if (flownodeInstance.isCompleted()) {
      processInstanceProcessingContext.getProcessingStatistics().increaseFlowNodesFinished();
    }

    ProcessInstance processInstance = processInstanceProcessingContext.getProcessInstance();
    selectNextNodeIfAllowedStart(
        processInstance,
        (I) flownodeInstance,
        currentVariableScope,
        flowNodeInstanceProcessingContext);

    processInstanceProcessingContext
        .getInstanceResult()
        .addInstanceUpdate(
            createFlowNodeInstanceUpdate(
                processInstance,
                flownodeInstance,
                currentVariableScope,
                now,
                flowNodeInstanceProcessingContext.getFlowElements()));
  }

  public final void processContinue(
      ProcessInstanceProcessingContext processInstanceProcessingContext,
      FlowNodeInstanceProcessingContext flowNodeInstanceProcessingContext,
      int subProcessLevel,
      FlowNodeInstance<?> flowNodeInstance,
      ContinueFlowElementTriggerDTO trigger,
      VariableScope parentVariableScope) {
    if (!flowNodeInstance.stateAllowsContinue()) {
      return;
    }

    VariableScope currentVariableScope =
        parentVariableScope.selectFlowNodeInstancesScope(flowNodeInstance.getElementInstanceId());

    processInstanceProcessingContext.getProcessingStatistics().increaseFlowNodesContinued();

    long now = clock.instant().toEpochMilli();

    this.processContinueSpecificFlowNodeInstance(
        processInstanceProcessingContext,
        flowNodeInstanceProcessingContext,
        subProcessLevel,
        (I) flowNodeInstance,
        (C) trigger,
        currentVariableScope);

    if (flowNodeInstance.isCompleted()) {
      processInstanceProcessingContext.getProcessingStatistics().increaseFlowNodesFinished();
    }

    ProcessInstance processInstance = processInstanceProcessingContext.getProcessInstance();
    selectNextNodeIfAllowedContinue(
        (I) flowNodeInstance,
        processInstance,
        currentVariableScope,
        flowNodeInstanceProcessingContext);

    processInstanceProcessingContext
        .getInstanceResult()
        .addInstanceUpdate(
            createFlowNodeInstanceUpdate(
                processInstance,
                flowNodeInstance,
                currentVariableScope,
                now,
                flowNodeInstanceProcessingContext.getFlowElements()));
  }

  public void processTerminate(
      ProcessInstanceProcessingContext processInstanceProcessingContext,
      FlowNodeInstanceProcessingContext flowNodeInstanceProcessingContext,
      FlowNodeInstance<?> instance,
      VariableScope parentVariablesScope) {
    // Only terminate if the instance is ready or waiting
    if (instance.stateAllowsTerminate()) {
      long now = clock.instant().toEpochMilli();

      VariableScope currentVariableScope =
          parentVariablesScope.selectFlowNodeInstancesScope(instance.getElementInstanceId());
      ProcessInstance processInstance = processInstanceProcessingContext.getProcessInstance();
      processTerminateSpecificFlowNodeInstance(
          processInstanceProcessingContext,
          flowNodeInstanceProcessingContext,
          (I) instance,
          currentVariableScope);

      instance.terminate();

      processInstanceProcessingContext.getProcessingStatistics().increaseFlowNodesFinished();

      processInstanceProcessingContext
          .getInstanceResult()
          .addInstanceUpdate(
              createFlowNodeInstanceUpdate(
                  processInstance,
                  instance,
                  currentVariableScope,
                  now,
                  flowNodeInstanceProcessingContext.getFlowElements()));
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
      VariableScope processInstanceVariables,
      FlowNodeInstanceProcessingContext flowNodeInstanceProcessingContext) {
    if (flownodeInstance.canSelectNextNodeStart()) {

      processNodeResultAndSelectNextInstance(
          processInstance,
          flownodeInstance,
          processInstanceVariables,
          flowNodeInstanceProcessingContext);
    }
  }

  protected void selectNextNodeIfAllowedContinue(
      I flownodeInstance,
      ProcessInstance processInstance,
      VariableScope currentVariableScope,
      FlowNodeInstanceProcessingContext flowNodeInstanceProcessingContext) {
    if (flownodeInstance.canSelectNextNodeContinue()) {

      processNodeResultAndSelectNextInstance(
          processInstance,
          flownodeInstance,
          currentVariableScope,
          flowNodeInstanceProcessingContext);
    }
  }

  protected void processNodeResultAndSelectNextInstance(
      ProcessInstance processInstance,
      I flownodeInstance,
      VariableScope currentVariableScope,
      FlowNodeInstanceProcessingContext flowNodeInstanceProcessingContext) {
    FlowNode flowNode = flownodeInstance.getFlowNode();
    if (flowNode instanceof WithIoMapping withIoMapping) {
      ioMappingProcessor.processOutputMappings(withIoMapping, currentVariableScope);
    }

    flownodeInstance.increasePassedCnt();
    FlowNodeInstances flowNodeInstances = flowNodeInstanceProcessingContext.getFlowNodeInstances();
    getSelectedSequenceFlows(
            processInstance, flownodeInstance, flowNodeInstances, currentVariableScope)
        .forEach(
            sequenceFlow -> {
              FlowNodeInstance<?> newFlowNodeInstance =
                  sequenceFlow
                      .getTargetNode()
                      .createAndStoreNewInstance(
                          flownodeInstance.getParentInstance(), flowNodeInstances);
              flowNodeInstanceProcessingContext
                  .getDirectInstanceResult()
                  .addNewFlowNodeInstance(
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
      ProcessInstanceProcessingContext processInstanceProcessingContext,
      FlowNodeInstanceProcessingContext flowNodeInstanceProcessingContext,
      I flownodeInstance,
      String inputFlowId,
      VariableScope variables);

  protected abstract void processContinueSpecificFlowNodeInstance(
      ProcessInstanceProcessingContext processInstanceProcessingContext,
      FlowNodeInstanceProcessingContext flowNodeInstanceProcessingContext,
      int subProcessLevel,
      I flowNodeInstance,
      C trigger,
      VariableScope variables);

  protected abstract void processTerminateSpecificFlowNodeInstance(
      ProcessInstanceProcessingContext processInstanceProcessingContext,
      FlowNodeInstanceProcessingContext flowNodeInstanceProcessingContext,
      I instance,
      VariableScope currentVariableScope);

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
    String elementId = flowElements.getIndex().get(flowNodeInstanceDTO.getElementIndex());
    flowNodeInstanceDTO.setElementId(elementId);
    return new InstanceUpdate(
        processInstance.getProcessInstanceKey(),
        new FlowNodeInstanceUpdateDTO(
            elementInstanceIdPath, flowNodeInstanceDTO, processInstanceVariablesDTO, processTime));
  }
}
