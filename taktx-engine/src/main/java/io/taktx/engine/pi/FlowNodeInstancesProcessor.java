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
import io.taktx.dto.v_1_0_0.FlowNodeInstanceDTO;
import io.taktx.dto.v_1_0_0.FlowNodeInstanceKeyDTO;
import io.taktx.dto.v_1_0_0.ProcessInstanceState;
import io.taktx.dto.v_1_0_0.TerminateTriggerDTO;
import io.taktx.engine.pd.model.EventSignal;
import io.taktx.engine.pd.model.FlowElements;
import io.taktx.engine.pd.model.FlowNode;
import io.taktx.engine.pi.model.FlowNodeInstance;
import io.taktx.engine.pi.model.FlowNodeInstances;
import io.taktx.engine.pi.model.ProcessInstance;
import io.taktx.engine.pi.model.VariableScope;
import io.taktx.engine.pi.processor.FlowNodeInstanceProcessor;
import io.taktx.engine.pi.processor.FlowNodeInstanceProcessorProvider;
import jakarta.enterprise.context.ApplicationScoped;
import java.time.Clock;
import org.apache.kafka.streams.state.KeyValueStore;

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
      KeyValueStore<FlowNodeInstanceKeyDTO, FlowNodeInstanceDTO> flowNodeInstanceStore,
      InstanceResult instanceResult,
      String elementId,
      FlowNodeInstance<?> parentElementInstance,
      FlowElements flowElements,
      ProcessInstance processInstance,
      VariableScope parentVariableScope,
      FlowNodeInstances flowNodeInstances,
      ProcessingStatistics processingStatistics) {

    flowNodeInstances.setState(ProcessInstanceState.ACTIVE);

    DirectInstanceResult directInstanceResult = DirectInstanceResult.empty();

    FlowNode flowNode = flowElements.getStartNode(elementId);
    FlowNodeInstance<?> flowNodeInstance =
        flowNode.createAndStoreNewInstance(parentElementInstance, flowNodeInstances);

    FlowNodeInstanceProcessor<?, ?, ?> processor =
        flowNodeInstanceProcessorProvider.getProcessor(flowNode);

    processor.processStart(
        flowNodeInstanceStore,
        instanceResult,
        directInstanceResult,
        flowElements,
        flowNodeInstance,
        processInstance,
        null,
        parentVariableScope,
        flowNodeInstances,
        processingStatistics);

    continueNewInstances(
        flowNodeInstanceStore,
        instanceResult,
        directInstanceResult,
        flowNodeInstances,
        flowElements,
        processInstance,
        parentVariableScope,
        processingStatistics);

    flowNodeInstances.determineImplicitCompletedState();
  }

  public void processContinue(
      KeyValueStore<FlowNodeInstanceKeyDTO, FlowNodeInstanceDTO> flowNodeInstanceStore,
      InstanceResult instanceResult,
      int subProcessLevel,
      ContinueFlowElementTriggerDTO trigger,
      FlowElements flowElements,
      ProcessInstance processInstance,
      VariableScope parentVariables,
      FlowNodeInstances flowNodeInstances,
      ProcessingStatistics processingStatistics) {

    StoredFlowNodeInstancesWrapper storedFlowNodeInstancesWrapper =
        new StoredFlowNodeInstancesWrapper(
            processInstance.getProcessInstanceKey(),
            flowNodeInstances,
            flowNodeInstanceStore,
            flowElements);

    FlowNodeInstance<?> flowNodeInstance =
        storedFlowNodeInstancesWrapper.getInstanceWithInstanceId(
            trigger.getElementInstanceIdPath().get(subProcessLevel));

    DirectInstanceResult directInstanceResult = DirectInstanceResult.empty();

    FlowNodeInstanceProcessor<?, ?, ?> processor =
        flowNodeInstanceProcessorProvider.getProcessor(flowNodeInstance.getFlowNode());

    processor.processContinue(
        flowNodeInstanceStore,
        instanceResult,
        directInstanceResult,
        subProcessLevel,
        flowElements,
        processInstance,
        flowNodeInstance,
        trigger,
        parentVariables,
        flowNodeInstances,
        processingStatistics);

    continueNewInstances(
        flowNodeInstanceStore,
        instanceResult,
        directInstanceResult,
        flowNodeInstances,
        flowElements,
        processInstance,
        parentVariables,
        processingStatistics);

    EventSignal eventSignal = directInstanceResult.pollBubbleUpEvent();
    while (eventSignal != null) {
      instanceResult.addBubbleUpEvent(eventSignal);
      eventSignal = directInstanceResult.pollBubbleUpEvent();
    }

    flowNodeInstances.determineImplicitCompletedState();
  }

  public void processTerminate(
      KeyValueStore<FlowNodeInstanceKeyDTO, FlowNodeInstanceDTO> flowNodeInstanceStore,
      InstanceResult instanceResult,
      TerminateTriggerDTO trigger,
      ProcessInstance processInstance,
      FlowNodeInstances flowNodeInstances,
      VariableScope parentVariableScope,
      FlowElements flowElements,
      ProcessingStatistics processingStatistics) {

    DirectInstanceResult directInstanceResult = DirectInstanceResult.empty();

    StoredFlowNodeInstancesWrapper storedFlowNodeInstancesWrapper =
        new StoredFlowNodeInstancesWrapper(
            processInstance.getProcessInstanceKey(),
            flowNodeInstances,
            flowNodeInstanceStore,
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
                    flowNodeInstanceStore,
                    instanceResult,
                    directInstanceResult,
                    instance,
                    processInstance,
                    parentVariableScope,
                    flowNodeInstances,
                    processingStatistics,
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
            flowNodeInstanceStore,
            instanceResult,
            directInstanceResult,
            instance,
            processInstance,
            parentVariableScope,
            flowNodeInstances,
            processingStatistics,
            flowElements);
      }
    }
    flowNodeInstances.determineImplicitCompletedState();
  }

  protected void continueNewInstances(
      KeyValueStore<FlowNodeInstanceKeyDTO, FlowNodeInstanceDTO> flowNodeInstanceStore,
      InstanceResult instanceResult,
      DirectInstanceResult directInstanceResult,
      FlowNodeInstances flowNodeInstances,
      FlowElements flowElements,
      ProcessInstance processInstance,
      VariableScope parentVariableScope,
      ProcessingStatistics processingStatistics) {

    flowInstanceRunner.continueNewInstances(
        flowNodeInstanceStore,
        instanceResult,
        directInstanceResult,
        flowNodeInstances,
        processInstance,
        flowElements,
        parentVariableScope,
        processingStatistics);
  }
}
