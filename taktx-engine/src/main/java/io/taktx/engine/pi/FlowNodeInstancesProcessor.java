/*
 *
 *  * TaktX - A high-performance BPMN engine
 *  * Copyright (c) 2025 TaktX B.V. All rights reserved.
 *  * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 *  * Free use is permitted with up to 3 Kafka partitions. See LICENSE file for details.
 *  * For commercial use or more partitions and features, contact [info@taktx.io] or [https://www.taktx.io/contact].
 *
 */

package io.taktx.engine.pi;

import io.taktx.dto.ContinueFlowElementTriggerDTO;
import io.taktx.dto.ProcessInstanceState;
import io.taktx.dto.TerminateTriggerDTO;
import io.taktx.engine.pd.model.EventSignal;
import io.taktx.engine.pd.model.FlowElements;
import io.taktx.engine.pd.model.FlowNode;
import io.taktx.engine.pi.model.FlowNodeInstance;
import io.taktx.engine.pi.model.FlowNodeInstances;
import io.taktx.engine.pi.model.VariableScope;
import io.taktx.engine.pi.processor.FlowNodeInstanceProcessor;
import io.taktx.engine.pi.processor.FlowNodeInstanceProcessorProvider;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class FlowNodeInstancesProcessor {

  private final FlowNodeInstanceProcessorProvider flowNodeInstanceProcessorProvider;
  private final FlowInstanceRunner flowInstanceRunner;

  public FlowNodeInstancesProcessor(
      FlowNodeInstanceProcessorProvider flowNodeInstanceProcessorProvider,
      FlowInstanceRunner flowInstanceRunner) {
    this.flowNodeInstanceProcessorProvider = flowNodeInstanceProcessorProvider;
    this.flowInstanceRunner = flowInstanceRunner;
  }

  public void processStart(
      ProcessInstanceProcessingContext processInstanceProcessingContext,
      FlowNodeInstanceProcessingContext flowNodeInstanceProcessingContext,
      String elementId,
      FlowNodeInstance<?> parentElementInstance,
      VariableScope parentVariableScope) {

    FlowNodeInstances flowNodeInstances = flowNodeInstanceProcessingContext.getFlowNodeInstances();
    flowNodeInstances.setState(ProcessInstanceState.ACTIVE);

    FlowNode flowNode = flowNodeInstanceProcessingContext.getFlowElements().getStartNode(elementId);
    FlowNodeInstance<?> flowNodeInstance =
        flowNode.createAndStoreNewInstance(parentElementInstance, flowNodeInstances);

    FlowNodeInstanceProcessor<?, ?, ?> processor =
        flowNodeInstanceProcessorProvider.getProcessor(flowNode);

    processor.processStart(
        processInstanceProcessingContext,
        flowNodeInstanceProcessingContext,
        flowNodeInstance,
        null,
        parentVariableScope);

    continueNewInstances(
        processInstanceProcessingContext, flowNodeInstanceProcessingContext, parentVariableScope);

    flowNodeInstances.determineImplicitCompletedState();
  }

  public void processContinue(
      ProcessInstanceProcessingContext processInstanceProcessingContext,
      FlowNodeInstanceProcessingContext flowNodeInstanceProcessingContext,
      int subProcessLevel,
      ContinueFlowElementTriggerDTO trigger,
      VariableScope parentVariables) {

    StoredFlowNodeInstancesWrapper storedFlowNodeInstancesWrapper =
        new StoredFlowNodeInstancesWrapper(
            processInstanceProcessingContext.getProcessInstance().getProcessInstanceKey(),
            flowNodeInstanceProcessingContext.getFlowNodeInstances(),
            processInstanceProcessingContext.getFlowNodeInstanceStore(),
            flowNodeInstanceProcessingContext.getFlowElements());

    FlowNodeInstance<?> flowNodeInstance =
        storedFlowNodeInstancesWrapper.getInstanceWithInstanceId(
            trigger.getElementInstanceIdPath().get(subProcessLevel));

    FlowNodeInstanceProcessor<?, ?, ?> processor =
        flowNodeInstanceProcessorProvider.getProcessor(flowNodeInstance.getFlowNode());

    processor.processContinue(
        processInstanceProcessingContext,
        flowNodeInstanceProcessingContext,
        subProcessLevel,
        flowNodeInstance,
        trigger,
        parentVariables);

    continueNewInstances(
        processInstanceProcessingContext, flowNodeInstanceProcessingContext, parentVariables);

    DirectInstanceResult directInstanceResult =
        flowNodeInstanceProcessingContext.getDirectInstanceResult();
    EventSignal eventSignal = directInstanceResult.pollBubbleUpEvent();
    while (eventSignal != null) {
      processInstanceProcessingContext.getInstanceResult().addBubbleUpEvent(eventSignal);
      eventSignal = directInstanceResult.pollBubbleUpEvent();
    }

    flowNodeInstanceProcessingContext.getFlowNodeInstances().determineImplicitCompletedState();
  }

  public void processTerminate(
      ProcessInstanceProcessingContext processInstanceProcessingContext,
      TerminateTriggerDTO trigger,
      FlowNodeInstances flowNodeInstances,
      VariableScope parentVariableScope,
      FlowElements flowElements) {

    StoredFlowNodeInstancesWrapper storedFlowNodeInstancesWrapper =
        new StoredFlowNodeInstancesWrapper(
            processInstanceProcessingContext.getProcessInstance().getProcessInstanceKey(),
            flowNodeInstances,
            processInstanceProcessingContext.getFlowNodeInstanceStore(),
            flowElements);

    FlowNodeInstanceProcessingContext flowNodeInstanceProcessingContext =
        new FlowNodeInstanceProcessingContext(flowNodeInstances, flowElements);
    if (trigger.getElementInstanceIdPath().isEmpty()) {
      // Terminate all elements in the process instance and the process instance itself
      storedFlowNodeInstancesWrapper
          .getAllInstances()
          .values()
          .forEach(
              instance -> {
                FlowNodeInstanceProcessor<?, ?, ?> processor =
                    flowNodeInstanceProcessorProvider.getProcessor(instance.getFlowNode());
                processor.processTerminate(
                    processInstanceProcessingContext,
                    flowNodeInstanceProcessingContext,
                    instance,
                    parentVariableScope);
              });
      flowNodeInstances.setState(ProcessInstanceState.TERMINATED);
    } else {
      // Terminate the specific element instance in the process instance
      FlowNodeInstance<?> instance =
          storedFlowNodeInstancesWrapper.getInstanceWithInstanceId(
              trigger.getElementInstanceIdPath().getFirst());
      if (instance != null) {
        FlowNodeInstanceProcessor<?, ?, ?> processor =
            flowNodeInstanceProcessorProvider.getProcessor(instance.getFlowNode());
        processor.processTerminate(
            processInstanceProcessingContext,
            flowNodeInstanceProcessingContext,
            instance,
            parentVariableScope);
      }
    }
    flowNodeInstances.determineImplicitCompletedState();
  }

  protected void continueNewInstances(
      ProcessInstanceProcessingContext processInstanceProcessingContext,
      FlowNodeInstanceProcessingContext flowNodeInstanceProcessingContext,
      VariableScope parentVariableScope) {

    flowInstanceRunner.continueNewInstances(
        processInstanceProcessingContext, flowNodeInstanceProcessingContext, parentVariableScope);
  }
}
