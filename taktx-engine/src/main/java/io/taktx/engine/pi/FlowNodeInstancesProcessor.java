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

import io.taktx.dto.v_1_0_0.ContinueFlowElementTriggerDTO;
import io.taktx.dto.v_1_0_0.ProcessInstanceState;
import io.taktx.dto.v_1_0_0.TerminateTriggerDTO;
import io.taktx.engine.pd.model.EventSignal;
import io.taktx.engine.pd.model.FlowElements;
import io.taktx.engine.pd.model.FlowNode;
import io.taktx.engine.pi.model.FlowNodeInstance;
import io.taktx.engine.pi.model.FlowNodeInstances;
import io.taktx.engine.pi.model.VariableScope;
import io.taktx.engine.pi.processor.FlowNodeInstanceProcessor;
import io.taktx.engine.pi.processor.FlowNodeInstanceProcessorProvider;
import jakarta.enterprise.context.ApplicationScoped;
import java.time.Clock;

@ApplicationScoped
public class FlowNodeInstancesProcessor {

  private final FlowNodeInstanceProcessorProvider flowNodeInstanceProcessorProvider;
  private final FlowInstanceRunner flowInstanceRunner;
  private final Clock clock;

  public FlowNodeInstancesProcessor(
      FlowNodeInstanceProcessorProvider flowNodeInstanceProcessorProvider,
      FlowInstanceRunner flowInstanceRunner,
      Clock clock) {
    this.flowNodeInstanceProcessorProvider = flowNodeInstanceProcessorProvider;
    this.flowInstanceRunner = flowInstanceRunner;
    this.clock = clock;
  }

  public void processStart(
      ProcessingContext processingContext,
      String elementId,
      FlowNodeInstance<?> parentElementInstance,
      FlowElements flowElements,
      VariableScope parentVariableScope,
      FlowNodeInstances flowNodeInstances) {

    flowNodeInstances.setState(ProcessInstanceState.ACTIVE);

    DirectInstanceResult directInstanceResult = DirectInstanceResult.empty();

    FlowNode flowNode = flowElements.getStartNode(elementId);
    FlowNodeInstance<?> flowNodeInstance =
        flowNode.createAndStoreNewInstance(parentElementInstance, flowNodeInstances);

    FlowNodeInstanceProcessor<?, ?, ?> processor =
        flowNodeInstanceProcessorProvider.getProcessor(flowNode);

    processor.processStart(
        processingContext,
        directInstanceResult,
        flowElements,
        flowNodeInstance,
        null,
        parentVariableScope,
        flowNodeInstances);

    continueNewInstances(
        processingContext,
        directInstanceResult,
        flowNodeInstances,
        flowElements,
        parentVariableScope);

    flowNodeInstances.determineImplicitCompletedState();
  }

  public void processContinue(
      ProcessingContext processingContext,
      int subProcessLevel,
      ContinueFlowElementTriggerDTO trigger,
      FlowElements flowElements,
      VariableScope parentVariables,
      FlowNodeInstances flowNodeInstances) {

    StoredFlowNodeInstancesWrapper storedFlowNodeInstancesWrapper =
        new StoredFlowNodeInstancesWrapper(
            processingContext.getProcessInstance().getProcessInstanceKey(),
            flowNodeInstances,
            processingContext.getFlowNodeInstanceStore(),
            flowElements);

    FlowNodeInstance<?> flowNodeInstance =
        storedFlowNodeInstancesWrapper.getInstanceWithInstanceId(
            trigger.getElementInstanceIdPath().get(subProcessLevel));

    DirectInstanceResult directInstanceResult = DirectInstanceResult.empty();

    FlowNodeInstanceProcessor<?, ?, ?> processor =
        flowNodeInstanceProcessorProvider.getProcessor(flowNodeInstance.getFlowNode());

    processor.processContinue(
        processingContext,
        directInstanceResult,
        subProcessLevel,
        flowElements,
        flowNodeInstance,
        trigger,
        parentVariables,
        flowNodeInstances);

    continueNewInstances(
        processingContext, directInstanceResult, flowNodeInstances, flowElements, parentVariables);

    EventSignal eventSignal = directInstanceResult.pollBubbleUpEvent();
    while (eventSignal != null) {
      processingContext.getInstanceResult().addBubbleUpEvent(eventSignal);
      eventSignal = directInstanceResult.pollBubbleUpEvent();
    }

    flowNodeInstances.determineImplicitCompletedState();
  }

  public void processTerminate(
      ProcessingContext processingContext,
      TerminateTriggerDTO trigger,
      FlowNodeInstances flowNodeInstances,
      VariableScope parentVariableScope,
      FlowElements flowElements) {

    DirectInstanceResult directInstanceResult = DirectInstanceResult.empty();

    StoredFlowNodeInstancesWrapper storedFlowNodeInstancesWrapper =
        new StoredFlowNodeInstancesWrapper(
            processingContext.getProcessInstance().getProcessInstanceKey(),
            flowNodeInstances,
            processingContext.getFlowNodeInstanceStore(),
            flowElements);

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
                    processingContext,
                    directInstanceResult,
                    instance,
                    parentVariableScope,
                    flowNodeInstances,
                    flowElements);
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
            processingContext,
            directInstanceResult,
            instance,
            parentVariableScope,
            flowNodeInstances,
            flowElements);
      }
    }
    flowNodeInstances.determineImplicitCompletedState();
  }

  protected void continueNewInstances(
      ProcessingContext processingContext,
      DirectInstanceResult directInstanceResult,
      FlowNodeInstances flowNodeInstances,
      FlowElements flowElements,
      VariableScope parentVariableScope) {

    flowInstanceRunner.continueNewInstances(
        processingContext,
        directInstanceResult,
        flowNodeInstances,
        flowElements,
        parentVariableScope);
  }
}
