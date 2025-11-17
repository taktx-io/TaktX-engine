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
import java.util.ArrayList;
import java.util.Collections;
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
      FlowNodeInstance<?> flowNodeInstance,
      String sequenceFlowId) {

    if (!flowNodeInstance.stateAllowsStart()) {
      return;
    }

    processInstanceProcessingContext.getProcessingStatistics().increaseFlowNodesStarted();

    long now = clock.instant().toEpochMilli();

    E flowNode = (E) flowNodeInstance.getFlowNode();

    addInputVariablesToScope(flowNode, scope.getVariableScope());

    this.processStartSpecificFlowNodeInstance(
        processInstanceProcessingContext, scope, (I) flowNodeInstance, sequenceFlowId);

    if (flowNodeInstance.getState().isDone()) {
      processInstanceProcessingContext.getProcessingStatistics().increaseFlowNodesFinished();
    }

    ProcessInstance processInstance = processInstanceProcessingContext.getProcessInstance();

    selectNextNodeIfAllowedStart(processInstance, (I) flowNodeInstance, scope);

    if (flowNodeInstance.isDirty()) {
      List<String> sequenceFlowIdList =
          sequenceFlowId != null ? List.of(sequenceFlowId) : Collections.emptyList();

      processInstanceProcessingContext
          .getInstanceResult()
          .addInstanceUpdate(
              createFlowNodeInstanceUpdate(
                  processInstance, flowNodeInstance, scope, now, sequenceFlowIdList));
    }
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

    if (flowNodeInstance.isDirty()) {
      List<String> sequenceFlowIds =
          scope.getDirectInstanceResult().getSequenceFlowsFromNewFlowNodeInstances();
      processInstanceProcessingContext
          .getInstanceResult()
          .addInstanceUpdate(
              createFlowNodeInstanceUpdate(
                  processInstance, flowNodeInstance, scope, now, sequenceFlowIds));
    }
  }

  public void processAbort(
      ProcessInstanceProcessingContext processInstanceProcessingContext,
      Scope scope,
      FlowNodeInstance<?> instance) {
    // Only terminate if the instanceToContinue is ready or waiting
    if (instance.stateAllowsStopping()) {
      long now = clock.instant().toEpochMilli();

      ProcessInstance processInstance = processInstanceProcessingContext.getProcessInstance();
      processAbortSpecificFlowNodeInstance(processInstanceProcessingContext, scope, (I) instance);

      instance.abort();

      processInstanceProcessingContext.getProcessingStatistics().increaseFlowNodesFinished();

      processInstanceProcessingContext
          .getInstanceResult()
          .addInstanceUpdate(
              createFlowNodeInstanceUpdate(
                  processInstance, instance, scope, now, Collections.emptyList()));
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

  protected List<FlowNodeInstance<?>> processNodeResultAndSelectNextInstance(
      ProcessInstance processInstance, I flownodeInstance, Scope scope) {
    FlowNode flowNode = flownodeInstance.getFlowNode();
    if (flowNode instanceof WithIoMapping withIoMapping) {
      ioMappingProcessor.processOutputMappings(withIoMapping, scope.getVariableScope());
    }

    flownodeInstance.increasePassedCnt();
    List<FlowNodeInstance<?>> newFlowNodeInstances = new ArrayList<>();
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
                      scope.getFlowNodeInstances().getInstanceWithInstanceId(gatewayInstanceId);
                }
              } else {
                newFlowNodeInstance =
                    sequenceFlow
                        .getTargetNode()
                        .createAndStoreNewInstance(flownodeInstance.getParentInstance(), scope);
              }
              newFlowNodeInstances.add(newFlowNodeInstance);
              scope
                  .getDirectInstanceResult()
                  .addNewFlowNodeInstance(
                      processInstance,
                      new StartFlowNodeInstanceInfo(newFlowNodeInstance, sequenceFlow.getId()));
            });
    return newFlowNodeInstances;
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

  protected abstract void processAbortSpecificFlowNodeInstance(
      ProcessInstanceProcessingContext processInstanceProcessingContext, Scope scope, I instance);

  protected InstanceUpdate createFlowNodeInstanceUpdate(
      ProcessInstance processInstance,
      FlowNodeInstance<?> flowNodeInstance,
      Scope scope,
      long processTime,
      List<String> sequenceFlowIds) {
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
            elementInstanceIdPath,
            flowNodeInstanceDTO,
            processInstanceVariablesDTO,
            processTime,
            sequenceFlowIds));
  }
}
