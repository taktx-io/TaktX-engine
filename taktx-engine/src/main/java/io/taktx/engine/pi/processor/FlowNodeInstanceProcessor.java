/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks All rights reserved.
 * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 * Free use is permitted with up to 3 Kafka partitions per topic. See LICENSE file for details.
 * For commercial use or more partitions and features, contact [https://www.taktx.io/contact].
 */

package io.taktx.engine.pi.processor;

import io.taktx.dto.ContinueFlowElementTriggerDTO;
import io.taktx.dto.FlowNodeInstanceDTO;
import io.taktx.dto.FlowNodeInstanceUpdateDTO;
import io.taktx.dto.VariablesDTO;
import io.taktx.engine.pd.model.FlowElements;
import io.taktx.engine.pd.model.FlowNode;
import io.taktx.engine.pd.model.Gateway;
import io.taktx.engine.pd.model.SequenceFlow;
import io.taktx.engine.pd.model.WithIoMapping;
import io.taktx.engine.pi.FlowNodeInstanceProcessingContext;
import io.taktx.engine.pi.InstanceUpdate;
import io.taktx.engine.pi.ProcessInstanceMapper;
import io.taktx.engine.pi.ProcessInstanceProcessingContext;
import io.taktx.engine.pi.StoredFlowNodeInstancesWrapper;
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
        flowNodeInstanceProcessingContext,
        processInstanceProcessingContext);

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
        flowNodeInstanceProcessingContext,
        processInstanceProcessingContext);

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
    // Only terminate if the instanceToContinue is ready or waiting
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
      FlowNodeInstanceProcessingContext flowNodeInstanceProcessingContext,
      ProcessInstanceProcessingContext processInstanceProcessingContext) {
    if (flownodeInstance.canSelectNextNodeStart()) {

      processNodeResultAndSelectNextInstance(
          processInstance,
          flownodeInstance,
          processInstanceVariables,
          flowNodeInstanceProcessingContext,
          processInstanceProcessingContext);
    }
  }

  protected void selectNextNodeIfAllowedContinue(
      I flownodeInstance,
      ProcessInstance processInstance,
      VariableScope currentVariableScope,
      FlowNodeInstanceProcessingContext flowNodeInstanceProcessingContext,
      ProcessInstanceProcessingContext processInstanceProcessingContext) {
    if (flownodeInstance.canSelectNextNodeContinue()) {

      processNodeResultAndSelectNextInstance(
          processInstance,
          flownodeInstance,
          currentVariableScope,
          flowNodeInstanceProcessingContext,
          processInstanceProcessingContext);
    }
  }

  protected void processNodeResultAndSelectNextInstance(
      ProcessInstance processInstance,
      I flownodeInstance,
      VariableScope currentVariableScope,
      FlowNodeInstanceProcessingContext flowNodeInstanceProcessingContext,
      ProcessInstanceProcessingContext processInstanceProcessingContext) {
    FlowNode flowNode = flownodeInstance.getFlowNode();
    if (flowNode instanceof WithIoMapping withIoMapping) {
      ioMappingProcessor.processOutputMappings(withIoMapping, currentVariableScope);
    }

    StoredFlowNodeInstancesWrapper wrapper =
        new StoredFlowNodeInstancesWrapper(
            processInstance.getProcessInstanceId(),
            flowNodeInstanceProcessingContext.getFlowNodeInstances(),
            processInstanceProcessingContext.getFlowNodeInstanceStore(),
            flowNodeInstanceProcessingContext.getFlowElements(),
            processInstanceMapper);

    flownodeInstance.increasePassedCnt();
    FlowNodeInstances flowNodeInstances = flowNodeInstanceProcessingContext.getFlowNodeInstances();
    getSelectedSequenceFlows(
            processInstance, flownodeInstance, flowNodeInstances, currentVariableScope)
        .forEach(
            sequenceFlow -> {
              FlowNodeInstance<?> newFlowNodeInstance = null;
              if (sequenceFlow.getTargetNode() instanceof Gateway gateway) {
                Long gatewayInstanceId = flowNodeInstances.getGatewayInstanceId(gateway.getId());
                if (gatewayInstanceId == null) {
                  newFlowNodeInstance =
                      gateway.createAndStoreNewInstance(
                          flownodeInstance.getParentInstance(), flowNodeInstances);
                } else {
                  newFlowNodeInstance = wrapper.getInstanceWithInstanceId(gatewayInstanceId);
                }
              } else {
                newFlowNodeInstance =
                    sequenceFlow
                        .getTargetNode()
                        .createAndStoreNewInstance(
                            flownodeInstance.getParentInstance(), flowNodeInstances);
              }
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
        processInstance.getProcessInstanceId(),
        new FlowNodeInstanceUpdateDTO(
            elementInstanceIdPath, flowNodeInstanceDTO, processInstanceVariablesDTO, processTime));
  }
}
