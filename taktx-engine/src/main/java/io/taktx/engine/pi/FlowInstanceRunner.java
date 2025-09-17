/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks All rights reserved.
 * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 * Free use is permitted with up to 3 Kafka partitions per topic. See LICENSE file for details.
 * For commercial use or more partitions and features, contact [https://www.taktx.io/contact].
 */

package io.taktx.engine.pi;

import io.taktx.engine.pd.model.EventDefinition;
import io.taktx.engine.pd.model.EventSignal;
import io.taktx.engine.pd.model.FlowNode;
import io.taktx.engine.pd.model.StartEvent;
import io.taktx.engine.pd.model.SubProcess;
import io.taktx.engine.pi.model.ActivityInstance;
import io.taktx.engine.pi.model.BoundaryEventInstance;
import io.taktx.engine.pi.model.FlowNodeInstance;
import io.taktx.engine.pi.model.FlowNodeInstanceInfo;
import io.taktx.engine.pi.model.VariableScope;
import io.taktx.engine.pi.processor.BoundaryEventInstanceProcessor;
import io.taktx.engine.pi.processor.FlowNodeInstanceProcessor;
import io.taktx.engine.pi.processor.FlowNodeInstanceProcessorProvider;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;

@ApplicationScoped
@RequiredArgsConstructor
public class FlowInstanceRunner {

  private final FlowNodeInstanceProcessorProvider processInstanceProcessorProvider;
  private final BoundaryEventInstanceProcessor boundaryEventProcessor;
  private final ProcessInstanceMapper mapper;

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

    EventSignal event = directInstanceResult.pollEvent();
    while (event != null) {
      processEventByFlowNodeInstance(
          processInstanceProcessingContext,
          flowNodeInstanceProcessingContext,
          event,
          event.getCurrentInstance(),
          directInstanceResult,
          parentVariableScope);
      event = directInstanceResult.pollEvent();
    }

    Long terminateInstance = directInstanceResult.pollTerminateInstance();
    while (terminateInstance != null) {
      StoredFlowNodeInstancesWrapper storedFlowNodeInstancesWrapper =
          new StoredFlowNodeInstancesWrapper(
              processInstanceProcessingContext.getProcessInstance().getProcessInstanceId(),
              flowNodeInstanceProcessingContext.getFlowNodeInstances(),
              processInstanceProcessingContext.getFlowNodeInstanceStore(),
              flowNodeInstanceProcessingContext.getFlowElements(),
              mapper);
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
      terminateInstance = directInstanceResult.pollTerminateInstance();
    }

    FlowNodeInstanceInfo instanceInfo = directInstanceResult.pollNewFlowNodeInstance();
    while (instanceInfo != null) {
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
      instanceInfo = directInstanceResult.pollNewFlowNodeInstance();
    }

    List<Long> terminateParentPath = directInstanceResult.getTerminateParentPath();
    if (currentContextIsParentOfPath(terminateParentPath, flowNodeInstanceProcessingContext)) {

      StoredFlowNodeInstancesWrapper storedFlowNodeInstancesWrapper =
          new StoredFlowNodeInstancesWrapper(
              processInstanceProcessingContext.getProcessInstance().getProcessInstanceId(),
              flowNodeInstanceProcessingContext.getFlowNodeInstances(),
              processInstanceProcessingContext.getFlowNodeInstanceStore(),
              flowNodeInstanceProcessingContext.getFlowElements(),
              mapper);

      Map<Long, FlowNodeInstance<?>> allInstances =
          storedFlowNodeInstancesWrapper.getAllInstances();

      for (FlowNodeInstance<?> flowNodeInstance : allInstances.values()) {
        if (flowNodeInstance.isAwaiting()
            && flowNodeInstance.getElementInstanceId() != terminateParentPath.getLast()) {
          directInstanceResult.addTerminateInstance(flowNodeInstance.getElementInstanceId());
        }
      }
      directInstanceResult.setTerminateParentPath(null);
    }
  }

  private boolean currentContextIsParentOfPath(
      List<Long> terminateParentPath,
      FlowNodeInstanceProcessingContext flowNodeInstanceProcessingContext) {

    return terminateParentPath != null
        && flowNodeInstanceProcessingContext.getSubProcessLevel() == terminateParentPath.size() - 1;
  }

  private void processEventByFlowNodeInstance(
      ProcessInstanceProcessingContext processInstanceProcessingContext,
      FlowNodeInstanceProcessingContext flowNodeInstanceProcessingContext,
      EventSignal event,
      FlowNodeInstance<?> fLowNodeInstance,
      DirectInstanceResult directInstanceResult,
      VariableScope parentVariableScope) {
    boolean eventHandled = false;

    if (fLowNodeInstance instanceof ActivityInstance<?> activityInstance) {
      StoredFlowNodeInstancesWrapper instancesWrapper =
          new StoredFlowNodeInstancesWrapper(
              processInstanceProcessingContext.getProcessInstance().getProcessInstanceId(),
              flowNodeInstanceProcessingContext.getFlowNodeInstances(),
              processInstanceProcessingContext.getFlowNodeInstanceStore(),
              flowNodeInstanceProcessingContext.getFlowElements(),
              mapper);
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
    }
    // Still not handled
    if (!eventHandled) {
      // First check any event subprocesses which are able to handle this event

      // First do a round for specific event codes
      List<SubProcess> eventTriggeredSubProcesses =
          flowNodeInstanceProcessingContext.getFlowElements().getEventTriggeredSubProcesses();
      for (SubProcess eventSsubProcess : eventTriggeredSubProcesses) {
        List<StartEvent> startEvents = eventSsubProcess.getElements().getStartEvents();
        for (StartEvent startEvent : startEvents) {
          Set<EventDefinition> eventDefinitions = startEvent.getEventDefinitions();
          for (EventDefinition eventDefinition : eventDefinitions) {
            if (eventDefinition.handlesEvent(event)) {
              // Create a new instanceToContinue for the event subprocess
              FlowNodeInstance<?> eventSubProcessInstance =
                  eventSsubProcess.createAndStoreNewInstance(
                      fLowNodeInstance.getParentInstance(),
                      flowNodeInstanceProcessingContext.getFlowNodeInstances());
              FlowNodeInstanceInfo flowNodeInstanceInfo =
                  new FlowNodeInstanceInfo(eventSubProcessInstance, null);
              directInstanceResult.addNewFlowNodeInstance(
                  processInstanceProcessingContext.getProcessInstance(), flowNodeInstanceInfo);
              eventHandled = true;
            }
          }
        }
      }

      // Now for catch all events
      if (!eventHandled) {
        for (SubProcess eventSsubProcess : eventTriggeredSubProcesses) {
          List<StartEvent> startEvents = eventSsubProcess.getElements().getStartEvents();
          for (StartEvent startEvent : startEvents) {
            Set<EventDefinition> eventDefinitions = startEvent.getEventDefinitions();
            for (EventDefinition eventDefinition : eventDefinitions) {
              if (eventDefinition.handlesEventCatchAll(event)) {
                // Create a new instanceToContinue for the event subprocess
                FlowNodeInstance<?> eventSubProcessInstance =
                    eventSsubProcess.createAndStoreNewInstance(
                        fLowNodeInstance.getParentInstance(),
                        flowNodeInstanceProcessingContext.getFlowNodeInstances());
                FlowNodeInstanceInfo flowNodeInstanceInfo =
                    new FlowNodeInstanceInfo(eventSubProcessInstance, null);
                directInstanceResult.addNewFlowNodeInstance(
                    processInstanceProcessingContext.getProcessInstance(), flowNodeInstanceInfo);
                eventHandled = true;
              }
            }
          }
        }
      }

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
