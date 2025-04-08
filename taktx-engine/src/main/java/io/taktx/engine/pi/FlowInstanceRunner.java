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

import io.taktx.engine.pd.model.EventSignal;
import io.taktx.engine.pd.model.FlowElements;
import io.taktx.engine.pd.model.FlowNode;
import io.taktx.engine.pi.model.ActivityInstance;
import io.taktx.engine.pi.model.BoundaryEventInstance;
import io.taktx.engine.pi.model.FlowNodeInstance;
import io.taktx.engine.pi.model.FlowNodeInstanceInfo;
import io.taktx.engine.pi.model.FlowNodeInstances;
import io.taktx.engine.pi.model.VariableScope;
import io.taktx.engine.pi.processor.BoundaryEventInstanceProcessor;
import io.taktx.engine.pi.processor.FlowNodeInstanceProcessor;
import io.taktx.engine.pi.processor.FlowNodeInstanceProcessorProvider;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;

@ApplicationScoped
@RequiredArgsConstructor
public class FlowInstanceRunner {

  private final FlowNodeInstanceProcessorProvider processInstanceProcessorProvider;
  private final BoundaryEventInstanceProcessor boundaryEventProcessor;

  public void continueNewInstances(
      ProcessingContext processingContext,
      DirectInstanceResult directInstanceResult,
      FlowNodeInstances flowNodeInstances,
      FlowElements flowElements,
      VariableScope parentVariableScope) {

    while (directInstanceResult.hasDirectTriggers()) {
      processDirectTriggers(
          processingContext,
          flowNodeInstances,
          directInstanceResult,
          flowElements,
          parentVariableScope);
    }
  }

  private void processDirectTriggers(
      ProcessingContext processingContext,
      FlowNodeInstances flowNodeInstances,
      DirectInstanceResult directInstanceResult,
      FlowElements flowElements,
      VariableScope parentVariableScope) {

    while (!directInstanceResult.eventsEmpty()) {
      EventSignal event = directInstanceResult.pollEvent();
      processEventByFlowNodeInstance(
          processingContext,
          flowNodeInstances,
          flowElements,
          event,
          event.getCurrentInstance(),
          directInstanceResult,
          parentVariableScope);
    }

    while (!directInstanceResult.terminateInstancesIsEmpty()) {
      long terminateInstance = directInstanceResult.pollTerminateInstance();
      StoredFlowNodeInstancesWrapper storedFlowNodeInstancesWrapper =
          new StoredFlowNodeInstancesWrapper(
              processingContext.getProcessInstance().getProcessInstanceKey(),
              flowNodeInstances,
              processingContext.getFlowNodeInstanceStore(),
              flowElements);
      FlowNodeInstance<?> flowNodeInstance =
          storedFlowNodeInstancesWrapper.getInstanceWithInstanceId(terminateInstance);

      FlowNode node = flowNodeInstance.getFlowNode();

      FlowNodeInstanceProcessor<?, ?, ?> processor =
          processInstanceProcessorProvider.getProcessor(node);
      processor.processTerminate(
          processingContext,
          directInstanceResult,
          flowNodeInstance,
          parentVariableScope,
          flowNodeInstances,
          flowElements);
    }

    while (!directInstanceResult.newFlowNodeInstancesIsEmpty()) {
      FlowNodeInstanceInfo instanceInfo = directInstanceResult.pollNewFlowNodeInstance();
      FlowNodeInstance<?> fLowNodeInstance = instanceInfo.flowNodeInstance();
      flowNodeInstances.putInstance(fLowNodeInstance);
      FlowNodeInstanceProcessor<?, ?, ?> processor =
          processInstanceProcessorProvider.getProcessor(fLowNodeInstance.getFlowNode());
      processor.processStart(
          processingContext,
          directInstanceResult,
          flowElements,
          instanceInfo.flowNodeInstance(),
          instanceInfo.inputSequenceFlowId(),
          parentVariableScope,
          flowNodeInstances);
    }
  }

  private void processEventByFlowNodeInstance(
      ProcessingContext processingContext,
      FlowNodeInstances flowNodeInstances,
      FlowElements flowElements,
      EventSignal event,
      FlowNodeInstance<?> fLowNodeInstance,
      DirectInstanceResult directInstanceResult,
      VariableScope parentVariableScope) {

    if (fLowNodeInstance instanceof ActivityInstance<?> activityInstance) {
      boolean eventHandled = false;
      StoredFlowNodeInstancesWrapper instancesWrapper =
          new StoredFlowNodeInstancesWrapper(
              processingContext.getProcessInstance().getProcessInstanceKey(),
              flowNodeInstances,
              processingContext.getFlowNodeInstanceStore(),
              flowElements);
      // First check for specific codes
      for (long boundaryEventId : activityInstance.getBoundaryEventIds()) {
        BoundaryEventInstance boundaryEventInstance =
            (BoundaryEventInstance) instancesWrapper.getInstanceWithInstanceId(boundaryEventId);

        eventHandled =
            boundaryEventProcessor.processEvent(
                processingContext,
                boundaryEventInstance,
                event,
                directInstanceResult,
                parentVariableScope,
                flowNodeInstances,
                flowElements);
        if (eventHandled) {
          if (boundaryEventInstance.getFlowNode().isCancelActivity()) {
            directInstanceResult.addTerminateInstance(
                boundaryEventInstance.getAttachedInstanceId());
          }
          break;
        }
      }

      if (!eventHandled) {
        // If not handled by specific codes, check for catch all
        for (long boundaryEventId : activityInstance.getBoundaryEventIds()) {
          BoundaryEventInstance boundaryEventInstance =
              (BoundaryEventInstance) instancesWrapper.getInstanceWithInstanceId(boundaryEventId);
          if (!eventHandled) {
            eventHandled =
                boundaryEventProcessor.processEventCatchAll(
                    processingContext,
                    boundaryEventInstance,
                    event,
                    directInstanceResult,
                    parentVariableScope,
                    flowNodeInstances,
                    flowElements);
          }
          if (eventHandled) {
            if (boundaryEventInstance.getFlowNode().isCancelActivity()) {
              directInstanceResult.addTerminateInstance(
                  boundaryEventInstance.getAttachedInstanceId());
            }
            break;
          }
        }
      }

      // Still not handled, bubble up if so defined
      if (!eventHandled && fLowNodeInstance.getParentInstance() != null) {
        directInstanceResult.addBubbleUpEvent(event);
      } else if (!eventHandled) {
        // Still not handled and No more bubbling up possible
        directInstanceResult.addTerminateInstance(
            event.getCurrentInstance().getElementInstanceId());
      }
    }
  }
}
