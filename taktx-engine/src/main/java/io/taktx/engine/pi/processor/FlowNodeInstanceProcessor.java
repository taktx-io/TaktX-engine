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
import io.taktx.engine.pi.model.IFlowNodeInstance;
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
      VariableScope variableScope,
      FlowNodeInstance<?> flowNodeInstance,
      String inputSequenceFlowId) {

    if (!flowNodeInstance.stateAllowsStart()) {
      return;
    }

    processInstanceProcessingContext.getProcessingStatistics().increaseFlowNodesStarted();

    long now = clock.instant().toEpochMilli();

    E flowNode = (E) flowNodeInstance.getFlowNode();

    addInputVariablesToScope(flowNode, variableScope);

    this.processStartSpecificFlowNodeInstance(
        processInstanceProcessingContext,
        scope,
        variableScope,
        (I) flowNodeInstance,
        inputSequenceFlowId);

    if (flowNodeInstance.getState().isDone()) {
      processInstanceProcessingContext.getProcessingStatistics().increaseFlowNodesFinished();
      if (flowNode instanceof WithIoMapping withIoMapping) {
        ioMappingProcessor.processOutputMappings(withIoMapping, variableScope);
      }
    }

    ProcessInstance processInstance = processInstanceProcessingContext.getProcessInstance();

    selectNextNodeIfAllowedStart(
        processInstance, (I) flowNodeInstance, scope, variableScope.getParentScope());

    if (flowNodeInstance.isDirty()) {
      List<String> outputSequenceFlowIds =
          scope.getDirectInstanceResult().getSequenceFlowsFromNewFlowNodeInstances();
      processInstanceProcessingContext
          .getInstanceResult()
          .addInstanceUpdate(
              createFlowNodeInstanceUpdate(
                  processInstance,
                  flowNodeInstance,
                  scope,
                  variableScope,
                  now,
                  inputSequenceFlowId,
                  outputSequenceFlowIds));
    }
  }

  public void processSetVariables(
      ProcessInstanceProcessingContext processInstanceProcessingContext,
      Scope scope,
      FlowNodeInstance<?> flowNodeInstance,
      VariableScope variableScope,
      VariablesDTO variables) {

    long now = clock.instant().toEpochMilli();

    variableScope.merge(variables);

    ProcessInstance processInstance = processInstanceProcessingContext.getProcessInstance();

    processInstanceProcessingContext
        .getInstanceResult()
        .addInstanceUpdate(
            createFlowNodeInstanceUpdate(
                processInstance,
                flowNodeInstance,
                scope,
                variableScope,
                now,
                null,
                Collections.emptyList()));
  }

  public final void processContinue(
      ProcessInstanceProcessingContext processInstanceProcessingContext,
      Scope scope,
      VariableScope variableScope,
      FlowNodeInstance<?> flowNodeInstance,
      ContinueFlowElementTriggerDTO trigger) {
    if (!flowNodeInstance.stateAllowsContinue()) {
      return;
    }

    processInstanceProcessingContext.getProcessingStatistics().increaseFlowNodesContinued();

    variableScope.merge(trigger.getVariables());

    long now = clock.instant().toEpochMilli();

    this.processContinueSpecificFlowNodeInstance(
        processInstanceProcessingContext, scope, variableScope, (I) flowNodeInstance, (C) trigger);

    if (flowNodeInstance.isDone()) {
      processInstanceProcessingContext.getProcessingStatistics().increaseFlowNodesFinished();
      if (flowNodeInstance.getFlowNode() instanceof WithIoMapping withIoMapping) {
        ioMappingProcessor.processOutputMappings(withIoMapping, variableScope);
      }
    }

    ProcessInstance processInstance = processInstanceProcessingContext.getProcessInstance();
    selectNextNodeIfAllowedContinue(
        (I) flowNodeInstance, processInstance, scope, variableScope.getParentScope());

    if (flowNodeInstance.isDirty()) {
      List<String> outputSequenceFlowIds =
          scope.getDirectInstanceResult().getSequenceFlowsFromNewFlowNodeInstances();
      processInstanceProcessingContext
          .getInstanceResult()
          .addInstanceUpdate(
              createFlowNodeInstanceUpdate(
                  processInstance,
                  flowNodeInstance,
                  scope,
                  variableScope,
                  now,
                  null,
                  outputSequenceFlowIds));
    }
  }

  public void processAbort(
      ProcessInstanceProcessingContext processInstanceProcessingContext,
      Scope scope,
      VariableScope variableScope,
      IFlowNodeInstance instance) {
    // Only terminate if the instanceToContinue is ready or waiting
    if (instance.stateAllowsStopping()) {
      long now = clock.instant().toEpochMilli();

      ProcessInstance processInstance = processInstanceProcessingContext.getProcessInstance();
      processAbortSpecificFlowNodeInstance(
          processInstanceProcessingContext, scope, variableScope, (I) instance);

      instance.abort();

      processInstanceProcessingContext.getProcessingStatistics().increaseFlowNodesFinished();

      processInstanceProcessingContext
          .getInstanceResult()
          .addInstanceUpdate(
              createFlowNodeInstanceUpdate(
                  processInstance, instance, scope, variableScope, now, null, null));
    }
  }

  protected void addInputVariablesToScope(E flowNode, VariableScope variableScope) {
    if (flowNode instanceof WithIoMapping withIoMapping) {
      ioMappingProcessor.processInputMappings(withIoMapping, variableScope);
    }
  }

  protected void selectNextNodeIfAllowedStart(
      ProcessInstance processInstance,
      I flownodeInstance,
      Scope scope,
      VariableScope variableScope) {
    if (flownodeInstance.canSelectNextNodeStart()) {
      processNodeResultAndSelectNextInstance(
          processInstance, flownodeInstance, scope, variableScope);
    }
  }

  protected void selectNextNodeIfAllowedContinue(
      I flownodeInstance,
      ProcessInstance processInstance,
      Scope scope,
      VariableScope variableScope) {
    if (flownodeInstance.canSelectNextNodeContinue()) {
      processNodeResultAndSelectNextInstance(
          processInstance, flownodeInstance, scope, variableScope);
    }
  }

  protected List<FlowNodeInstance<?>> processNodeResultAndSelectNextInstance(
      ProcessInstance processInstance,
      I flownodeInstance,
      Scope scope,
      VariableScope variableScope) {
    flownodeInstance.increasePassedCnt();
    List<FlowNodeInstance<?>> newFlowNodeInstances = new ArrayList<>();
    getSelectedSequenceFlows(processInstance, flownodeInstance, scope, variableScope)
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
              VariableScope childVariableScope =
                  variableScope.selectChildScope(newFlowNodeInstance);
              scope
                  .getDirectInstanceResult()
                  .addNewFlowNodeInstance(
                      new StartFlowNodeInstanceInfo(
                          newFlowNodeInstance, sequenceFlow.getId(), childVariableScope));
            });
    return newFlowNodeInstances;
  }

  protected abstract Set<SequenceFlow> getSelectedSequenceFlows(
      ProcessInstance processInstance,
      I flowNodeInstance,
      Scope scope,
      VariableScope variableScope);

  protected abstract void processStartSpecificFlowNodeInstance(
      ProcessInstanceProcessingContext processInstanceProcessingContext,
      Scope scope,
      VariableScope variableScope,
      I flownodeInstance,
      String inputFlowId);

  protected abstract void processContinueSpecificFlowNodeInstance(
      ProcessInstanceProcessingContext processInstanceProcessingContext,
      Scope scope,
      VariableScope variableScope,
      I flowNodeInstance,
      C trigger);

  protected abstract void processAbortSpecificFlowNodeInstance(
      ProcessInstanceProcessingContext processInstanceProcessingContext,
      Scope scope,
      VariableScope variableScope,
      I instance);

  protected InstanceUpdate createFlowNodeInstanceUpdate(
      ProcessInstance processInstance,
      IFlowNodeInstance flowNodeInstance,
      Scope scope,
      VariableScope variableScope,
      long processTime,
      String inputSequenceFlowId,
      List<String> outputSequenceFlowIds) {
    List<Long> elementInstanceIdPath = flowNodeInstance.createKeyPath();

    VariablesDTO variablesDTO = variableScope.scopeToDTO();
    FlowNodeInstanceDTO flowNodeInstanceDTO =
        processInstanceMapper.map((FlowNodeInstance<?>) flowNodeInstance, scope.getFlowElements());
    String elementId =
        scope.getFlowElements().getIndex().get(flowNodeInstanceDTO.getElementIndex());
    flowNodeInstanceDTO.setElementId(elementId);
    return new InstanceUpdate(
        processInstance.getProcessInstanceId(),
        new FlowNodeInstanceUpdateDTO(
            elementInstanceIdPath,
            flowNodeInstanceDTO,
            variablesDTO,
            processTime,
            inputSequenceFlowId,
            outputSequenceFlowIds));
  }
}
