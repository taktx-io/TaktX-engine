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
import io.taktx.engine.pd.model.FlowNode;
import io.taktx.engine.pd.model.Gateway;
import io.taktx.engine.pd.model.SequenceFlow;
import io.taktx.engine.pd.model.WithIoMapping;
import io.taktx.engine.pi.InstanceUpdate;
import io.taktx.engine.pi.ProcessInstanceMapper;
import io.taktx.engine.pi.ProcessInstanceProcessingContext;
import io.taktx.engine.pi.model.FlowNodeInstance;
import io.taktx.engine.pi.model.ProcessInstance;
import io.taktx.engine.pi.model.Scope;
import io.taktx.engine.pi.model.StartFlowNodeInstanceInfo;
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
      Scope scope,
      FlowNodeInstance<?> flownodeInstance,
      String inputFlowId) {

    if (!flownodeInstance.stateAllowsStart()) {
      return;
    }

    processInstanceProcessingContext.getProcessingStatistics().increaseFlowNodesStarted();

    long now = clock.instant().toEpochMilli();

    E flowNode = (E) flownodeInstance.getFlowNode();

    addInputVariablesToScope(flowNode, scope.getVariableScope());

    this.processStartSpecificFlowNodeInstance(
        processInstanceProcessingContext, scope, (I) flownodeInstance, inputFlowId);

    if (flownodeInstance.isDone()) {
      processInstanceProcessingContext.getProcessingStatistics().increaseFlowNodesFinished();
    }

    ProcessInstance processInstance = processInstanceProcessingContext.getProcessInstance();

    selectNextNodeIfAllowedStart(processInstance, (I) flownodeInstance, scope);

    processInstanceProcessingContext
        .getInstanceResult()
        .addInstanceUpdate(
            createFlowNodeInstanceUpdate(processInstance, flownodeInstance, scope, now));
  }

  public final void processContinue(
      ProcessInstanceProcessingContext processInstanceProcessingContext,
      Scope scope,
      FlowNodeInstance<?> flowNodeInstance,
      ContinueFlowElementTriggerDTO trigger) {
    if (!flowNodeInstance.stateAllowsContinue()) {
      return;
    }

    processInstanceProcessingContext.getProcessingStatistics().increaseFlowNodesContinued();

    long now = clock.instant().toEpochMilli();

    this.processContinueSpecificFlowNodeInstance(
        processInstanceProcessingContext, scope, (I) flowNodeInstance, (C) trigger);

    if (flowNodeInstance.isDone()) {
      processInstanceProcessingContext.getProcessingStatistics().increaseFlowNodesFinished();
    }

    ProcessInstance processInstance = processInstanceProcessingContext.getProcessInstance();
    selectNextNodeIfAllowedContinue((I) flowNodeInstance, processInstance, scope);

    processInstanceProcessingContext
        .getInstanceResult()
        .addInstanceUpdate(
            createFlowNodeInstanceUpdate(processInstance, flowNodeInstance, scope, now));
  }

  public void processAbort(
      ProcessInstanceProcessingContext processInstanceProcessingContext,
      Scope scope,
      FlowNodeInstance<?> instance) {
    // Only terminate if the instanceToContinue is ready or waiting
    if (instance.stateAllowsStopping()) {
      long now = clock.instant().toEpochMilli();

      ProcessInstance processInstance = processInstanceProcessingContext.getProcessInstance();
      processTerminateSpecificFlowNodeInstance(
          processInstanceProcessingContext, scope, (I) instance);

      instance.abort();

      processInstanceProcessingContext.getProcessingStatistics().increaseFlowNodesFinished();

      processInstanceProcessingContext
          .getInstanceResult()
          .addInstanceUpdate(createFlowNodeInstanceUpdate(processInstance, instance, scope, now));
    }
  }

  public void processCancel(
      ProcessInstanceProcessingContext processInstanceProcessingContext,
      Scope scope,
      FlowNodeInstance<?> instance) {
    // Only terminate if the instanceToContinue is ready or waiting
    if (instance.stateAllowsStopping()) {
      long now = clock.instant().toEpochMilli();

      ProcessInstance processInstance = processInstanceProcessingContext.getProcessInstance();
      processTerminateSpecificFlowNodeInstance(
          processInstanceProcessingContext, scope, (I) instance);

      instance.cancel();

      processInstanceProcessingContext.getProcessingStatistics().increaseFlowNodesFinished();

      processInstanceProcessingContext
          .getInstanceResult()
          .addInstanceUpdate(createFlowNodeInstanceUpdate(processInstance, instance, scope, now));
    }
  }

  protected void addInputVariablesToScope(E flowNode, VariableScope flowNodeInstanceVariables) {
    if (flowNode instanceof WithIoMapping withIoMapping) {
      ioMappingProcessor.addInputVariables(withIoMapping, flowNodeInstanceVariables);
    }
  }

  protected void selectNextNodeIfAllowedStart(
      ProcessInstance processInstance, I flownodeInstance, Scope scope) {
    if (flownodeInstance.canSelectNextNodeStart()) {

      processNodeResultAndSelectNextInstance(processInstance, flownodeInstance, scope);
    }
  }

  protected void selectNextNodeIfAllowedContinue(
      I flownodeInstance, ProcessInstance processInstance, Scope scope) {
    if (flownodeInstance.canSelectNextNodeContinue()) {

      processNodeResultAndSelectNextInstance(processInstance, flownodeInstance, scope);
    }
  }

  protected void processNodeResultAndSelectNextInstance(
      ProcessInstance processInstance, I flownodeInstance, Scope scope) {
    FlowNode flowNode = flownodeInstance.getFlowNode();
    if (flowNode instanceof WithIoMapping withIoMapping) {
      ioMappingProcessor.processOutputMappings(withIoMapping, scope.getVariableScope());
    }

    flownodeInstance.increasePassedCnt();
    getSelectedSequenceFlows(processInstance, flownodeInstance, scope)
        .forEach(
            sequenceFlow -> {
              FlowNodeInstance<?> newFlowNodeInstance = null;
              if (sequenceFlow.getTargetNode() instanceof Gateway gateway) {
                Long gatewayInstanceId = scope.getGatewayInstanceId(gateway.getId());
                if (gatewayInstanceId == null) {
                  newFlowNodeInstance =
                      gateway.createAndStoreNewInstance(
                          flownodeInstance.getParentInstance(), scope);
                } else {
                  newFlowNodeInstance =
                      scope.getFlowNodeInstanceScope().getInstanceWithInstanceId(gatewayInstanceId);
                }
              } else {
                newFlowNodeInstance =
                    sequenceFlow
                        .getTargetNode()
                        .createAndStoreNewInstance(flownodeInstance.getParentInstance(), scope);
              }
              scope
                  .getDirectInstanceResult()
                  .addNewFlowNodeInstance(
                      processInstance,
                      new StartFlowNodeInstanceInfo(newFlowNodeInstance, sequenceFlow.getId()));
            });
  }

  protected abstract Set<SequenceFlow> getSelectedSequenceFlows(
      ProcessInstance processInstance, I flowNodeInstance, Scope scope);

  protected abstract void processStartSpecificFlowNodeInstance(
      ProcessInstanceProcessingContext processInstanceProcessingContext,
      Scope scope,
      I flownodeInstance,
      String inputFlowId);

  protected abstract void processContinueSpecificFlowNodeInstance(
      ProcessInstanceProcessingContext processInstanceProcessingContext,
      Scope scope,
      I flowNodeInstance,
      C trigger);

  protected abstract void processTerminateSpecificFlowNodeInstance(
      ProcessInstanceProcessingContext processInstanceProcessingContext, Scope scope, I instance);

  protected InstanceUpdate createFlowNodeInstanceUpdate(
      ProcessInstance processInstance,
      FlowNodeInstance<?> flowNodeInstance,
      Scope scope,
      long processTime) {
    List<Long> elementInstanceIdPath = flowNodeInstance.createKeyPath();
    VariablesDTO processInstanceVariablesDTO = scope.getVariableScope().scopeToDTO();
    FlowNodeInstanceDTO flowNodeInstanceDTO =
        processInstanceMapper.map(flowNodeInstance, scope.getFlowElements());
    String elementId =
        scope.getFlowElements().getIndex().get(flowNodeInstanceDTO.getElementIndex());
    flowNodeInstanceDTO.setElementId(elementId);
    return new InstanceUpdate(
        processInstance.getProcessInstanceId(),
        new FlowNodeInstanceUpdateDTO(
            elementInstanceIdPath, flowNodeInstanceDTO, processInstanceVariablesDTO, processTime));
  }
}
