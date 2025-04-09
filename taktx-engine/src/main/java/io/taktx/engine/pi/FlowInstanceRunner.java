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
import io.taktx.engine.pd.model.FlowNode;
import io.taktx.engine.pi.model.ActivityInstance;
import io.taktx.engine.pi.model.BoundaryEventInstance;
import io.taktx.engine.pi.model.FlowNodeInstance;
import io.taktx.engine.pi.model.FlowNodeInstanceInfo;
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
      ProcessInstanceProcessingContext processInstanceProcessingContext,
      FlowNodeInstanceProcessingContext flowNodeInstanceProcessingContext,
      VariableScope parentVariableScope) {

    while (flowNodeInstanceProcessingContext.getDirectInstanceResult().hasDirectTriggers()) {
      processDirectTriggers(
          processInstanceProcessingContext, flowNodeInstanceProcessingContext, parentVariableScope);
    }
  }

  private void processDirectTriggers(
      ProcessInstanceProcessingContext processInstanceProcessingContext,
      FlowNodeInstanceProcessingContext flowNodeInstanceProcessingContext,
      VariableScope parentVariableScope) {

    DirectInstanceResult directInstanceResult =
        flowNodeInstanceProcessingContext.getDirectInstanceResult();
    while (!directInstanceResult.eventsEmpty()) {
      EventSignal event = directInstanceResult.pollEvent();
      processEventByFlowNodeInstance(
          processInstanceProcessingContext,
          flowNodeInstanceProcessingContext,
          event,
          event.getCurrentInstance(),
          directInstanceResult,
          parentVariableScope);
    }

    while (!directInstanceResult.terminateInstancesIsEmpty()) {
      long terminateInstance = directInstanceResult.pollTerminateInstance();
      StoredFlowNodeInstancesWrapper storedFlowNodeInstancesWrapper =
          new StoredFlowNodeInstancesWrapper(
              processInstanceProcessingContext.getProcessInstance().getProcessInstanceKey(),
              flowNodeInstanceProcessingContext.getFlowNodeInstances(),
              processInstanceProcessingContext.getFlowNodeInstanceStore(),
              flowNodeInstanceProcessingContext.getFlowElements());
      FlowNodeInstance<?> flowNodeInstance =
          storedFlowNodeInstancesWrapper.getInstanceWithInstanceId(terminateInstance);

      FlowNode node = flowNodeInstance.getFlowNode();

      FlowNodeInstanceProcessor<?, ?, ?> processor =
          processInstanceProcessorProvider.getProcessor(node);
      processor.processTerminate(
          processInstanceProcessingContext,
          flowNodeInstanceProcessingContext,
          flowNodeInstance,
          parentVariableScope);
    }

    while (!directInstanceResult.newFlowNodeInstancesIsEmpty()) {
      FlowNodeInstanceInfo instanceInfo = directInstanceResult.pollNewFlowNodeInstance();
      FlowNodeInstance<?> fLowNodeInstance = instanceInfo.flowNodeInstance();
      flowNodeInstanceProcessingContext.getFlowNodeInstances().putInstance(fLowNodeInstance);
      FlowNodeInstanceProcessor<?, ?, ?> processor =
          processInstanceProcessorProvider.getProcessor(fLowNodeInstance.getFlowNode());
      processor.processStart(
          processInstanceProcessingContext,
          flowNodeInstanceProcessingContext,
          fLowNodeInstance,
          instanceInfo.inputSequenceFlowId(),
          parentVariableScope);
    }
  }

  private void processEventByFlowNodeInstance(
      ProcessInstanceProcessingContext processInstanceProcessingContext,
      FlowNodeInstanceProcessingContext flowNodeInstanceProcessingContext,
      EventSignal event,
      FlowNodeInstance<?> fLowNodeInstance,
      DirectInstanceResult directInstanceResult,
      VariableScope parentVariableScope) {

    if (fLowNodeInstance instanceof ActivityInstance<?> activityInstance) {
      boolean eventHandled = false;
      StoredFlowNodeInstancesWrapper instancesWrapper =
          new StoredFlowNodeInstancesWrapper(
              processInstanceProcessingContext.getProcessInstance().getProcessInstanceKey(),
              flowNodeInstanceProcessingContext.getFlowNodeInstances(),
              processInstanceProcessingContext.getFlowNodeInstanceStore(),
              flowNodeInstanceProcessingContext.getFlowElements());
      // First check for specific codes
      for (long boundaryEventId : activityInstance.getBoundaryEventIds()) {
        BoundaryEventInstance boundaryEventInstance =
            (BoundaryEventInstance) instancesWrapper.getInstanceWithInstanceId(boundaryEventId);

        eventHandled =
            boundaryEventProcessor.processEvent(
                processInstanceProcessingContext,
                flowNodeInstanceProcessingContext,
                boundaryEventInstance,
                event,
                parentVariableScope);
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
          eventHandled =
              boundaryEventProcessor.processEventCatchAll(
                  processInstanceProcessingContext,
                  flowNodeInstanceProcessingContext,
                  boundaryEventInstance,
                  event,
                  directInstanceResult,
                  parentVariableScope);
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
