/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks All rights reserved.
 * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 * Free use is permitted with up to 3 Kafka partitions per topic. See LICENSE file for details.
 * For commercial use or more partitions and features, contact [https://www.taktx.io/contact].
 */

package io.taktx.engine.pi;

import com.fasterxml.jackson.databind.JsonNode;
import io.taktx.dto.AbortTriggerDTO;
import io.taktx.dto.ContinueFlowElementTriggerDTO;
import io.taktx.dto.EventSignalDTO;
import io.taktx.dto.EventSignalTriggerDTO;
import io.taktx.dto.ExecutionState;
import io.taktx.dto.VariablesDTO;
import io.taktx.engine.feel.FeelExpressionHandler;
import io.taktx.engine.pd.model.Activity;
import io.taktx.engine.pd.model.EventBasedGateway;
import io.taktx.engine.pd.model.EventDefinition;
import io.taktx.engine.pd.model.EventSignal;
import io.taktx.engine.pd.model.FlowNode;
import io.taktx.engine.pd.model.Message;
import io.taktx.engine.pd.model.MessageEventDefinition;
import io.taktx.engine.pd.model.StartEvent;
import io.taktx.engine.pd.model.SubProcess;
import io.taktx.engine.pd.model.TimerEventDefinition;
import io.taktx.engine.pi.model.ActivityInstance;
import io.taktx.engine.pi.model.BoundaryEventInstance;
import io.taktx.engine.pi.model.ContinueFlowNodeInstanceInfo;
import io.taktx.engine.pi.model.EventBasedGatewayInstance;
import io.taktx.engine.pi.model.FlowNodeInstance;
import io.taktx.engine.pi.model.IntermediateCatchEventInstance;
import io.taktx.engine.pi.model.NewCorrelationSubscriptionMessageEventInfo;
import io.taktx.engine.pi.model.ScheduledStartInfo;
import io.taktx.engine.pi.model.Scope;
import io.taktx.engine.pi.model.StartFlowNodeInstanceInfo;
import io.taktx.engine.pi.model.SubProcessInstance;
import io.taktx.engine.pi.model.TerminateCorrelationSubscriptionMessageEventInfo;
import io.taktx.engine.pi.model.VariableScope;
import io.taktx.engine.pi.model.WithScope;
import io.taktx.engine.pi.processor.BoundaryEventInstanceProcessor;
import io.taktx.engine.pi.processor.FlowNodeInstanceProcessor;
import io.taktx.engine.pi.processor.FlowNodeInstanceProcessorProvider;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.ProcessingException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;

@ApplicationScoped
@Slf4j
public class ScopeProcessor {

  private final FlowNodeInstanceProcessorProvider flowNodeInstanceProcessorProvider;
  private final FeelExpressionHandler feelExpressionHandler;
  private final BoundaryEventInstanceProcessor boundaryEventProcessor;
  private final DtoMapper dtoMapper;

  public ScopeProcessor(
      FlowNodeInstanceProcessorProvider flowNodeInstanceProcessorProvider,
      FeelExpressionHandler feelExpressionHandler,
      BoundaryEventInstanceProcessor boundaryEventProcessor,
      DtoMapper dtoMapper) {
    this.flowNodeInstanceProcessorProvider = flowNodeInstanceProcessorProvider;
    this.feelExpressionHandler = feelExpressionHandler;
    this.boundaryEventProcessor = boundaryEventProcessor;
    this.dtoMapper = dtoMapper;
  }

  public Void processStart(
      List<Long> parentElementInstanceIdPath,
      String elementId,
      VariablesDTO variables,
      ProcessInstanceProcessingContext processInstanceProcessingContext,
      Scope scope,
      VariableScope variableScope) {

    int subProcessLevel = scope.getSubProcessLevel();

    if (subProcessLevel < parentElementInstanceIdPath.size()) {
      FlowNodeInstance<?> instanceWithInstanceId =
          scope
              .getFlowNodeInstances()
              .getInstanceWithInstanceId(parentElementInstanceIdPath.get(subProcessLevel));
      if (instanceWithInstanceId == null) {
        // Element should be there. We can't start an element in a subprocess which is not there
        throw new IllegalArgumentException(
            "No element with instance id "
                + parentElementInstanceIdPath.get(subProcessLevel)
                + " found on level "
                + subProcessLevel);
      }

      if (instanceWithInstanceId instanceof WithScope withScope) {

        VariableScope childVariableScope = variableScope.selectChildScope(instanceWithInstanceId);
        processStart(
            parentElementInstanceIdPath,
            elementId,
            variables,
            processInstanceProcessingContext,
            withScope.getScope(),
            childVariableScope);

        bubbleUpEvents(scope, withScope);

        ContinueFlowElementTriggerDTO trigger =
            new ContinueFlowElementTriggerDTO(
                processInstanceProcessingContext.getProcessInstance().getProcessInstanceId(),
                parentElementInstanceIdPath,
                null,
                VariablesDTO.empty());
        scope
            .getDirectInstanceResult()
            .addContinueInstance(
                new ContinueFlowNodeInstanceInfo(
                    instanceWithInstanceId, trigger, childVariableScope));
        doBusiness(processInstanceProcessingContext, scope, variableScope);
      } else {
        throw new IllegalArgumentException(
            "Element with instance id "
                + parentElementInstanceIdPath.get(subProcessLevel)
                + " is not a scope");
      }
    } else if (subProcessLevel == parentElementInstanceIdPath.size()) {
      if (scope.getActiveCnt() == 0) {
        startSubscriptionsForEventSubprocesses(
            processInstanceProcessingContext, scope, variableScope);
      }

      StartFlowNodeInstanceInfo newInstanceInfo =
          createNewInstanceAndAddToDirectInstanceResult(scope, elementId, variableScope, variables);

      // If the new instance is an event triggered subprocess with an interrupting start event,
      if (newInstanceInfo.flowNodeInstance() instanceof SubProcessInstance subProcessInstance
          && subProcessInstance.getFlowNode().isTriggeredByEvent()) {
        FlowNode startNode = subProcessInstance.getFlowElements().getStartNode(null);
        if (startNode instanceof StartEvent startEvent && startEvent.isInterrupting()) {
          Map<Long, FlowNodeInstance<?>> allInstances =
              scope.getFlowNodeInstances().getAllInstances();
          for (FlowNodeInstance<?> instance : allInstances.values()) {
            if (instance.isActive()
                && instance.getElementInstanceId()
                    != newInstanceInfo.flowNodeInstance().getElementInstanceId()) {
              scope.getDirectInstanceResult().addAbortInstance(instance);
            }
          }
        }
      }

      doBusiness(processInstanceProcessingContext, scope, variableScope);
    } else {
      throw new IllegalArgumentException(
          "Subprocess level "
              + subProcessLevel
              + " is out of bounds for parentElementInstanceIdPath "
              + parentElementInstanceIdPath);
    }
    return null;
  }

  public Void processContinue(
      ProcessInstanceProcessingContext processInstanceProcessingContext,
      Scope scope,
      VariableScope variableScope,
      ContinueFlowElementTriggerDTO trigger,
      List<Long> elementInstanceIdPath) {

    int subProcessLevel = scope.getSubProcessLevel();
    if (subProcessLevel < elementInstanceIdPath.size() - 1) {
      FlowNodeInstance<?> instanceWithInstanceId =
          scope
              .getFlowNodeInstances()
              .getInstanceWithInstanceId(elementInstanceIdPath.get(subProcessLevel));
      VariableScope childVariableScope = variableScope.selectChildScope(instanceWithInstanceId);
      if (instanceWithInstanceId instanceof WithScope withScope) {
        processContinue(
            processInstanceProcessingContext,
            withScope.getScope(),
            childVariableScope,
            trigger,
            elementInstanceIdPath);
        bubbleUpEvents(scope, withScope);

        scope
            .getDirectInstanceResult()
            .addContinueInstance(
                new ContinueFlowNodeInstanceInfo(
                    instanceWithInstanceId, trigger, childVariableScope));

        doBusiness(processInstanceProcessingContext, scope, variableScope);
      } else {
        throw new IllegalArgumentException(
            "Element with instance id "
                + elementInstanceIdPath.get(subProcessLevel)
                + " is not a scope but "
                + instanceWithInstanceId.getClass().getName());
      }
    } else {
      FlowNodeInstance<?> flowNodeInstance =
          scope
              .getFlowNodeInstances()
              .getInstanceWithInstanceId(
                  trigger.getElementInstanceIdPath().get(scope.getSubProcessLevel()));
      VariableScope childVariableScope = variableScope.selectChildScope(flowNodeInstance);

      if (trigger instanceof EventSignalTriggerDTO eventSignalTriggerDTO) {
        for (EventSignalDTO eventSignalDTO : eventSignalTriggerDTO.getEventSignalList()) {
          EventSignal eventSignal = dtoMapper.map(eventSignalDTO);
          eventSignal.getPathToSource().addFirst(flowNodeInstance);
          scope.getDirectInstanceResult().addEvent(eventSignal);
        }

      } else {
        ContinueFlowNodeInstanceInfo continueInstance =
            new ContinueFlowNodeInstanceInfo(flowNodeInstance, trigger, childVariableScope);

        scope.getDirectInstanceResult().addContinueInstance(continueInstance);
      }
      doBusiness(processInstanceProcessingContext, scope, variableScope);
    }
    return null;
  }

  public Void processAbort(
      ProcessInstanceProcessingContext processInstanceProcessingContext,
      Scope scope,
      VariableScope variableScope,
      AbortTriggerDTO trigger) {
    int subProcessLevel = scope.getSubProcessLevel();

    if (subProcessLevel < trigger.getElementInstanceIdPath().size() - 1) {
      FlowNodeInstance<?> instanceWithInstanceId =
          scope
              .getFlowNodeInstances()
              .getInstanceWithInstanceId(trigger.getElementInstanceIdPath().get(subProcessLevel));
      VariableScope childVariableScope = variableScope.selectChildScope(instanceWithInstanceId);

      if (instanceWithInstanceId instanceof WithScope withScope) {
        processAbort(
            processInstanceProcessingContext, withScope.getScope(), childVariableScope, trigger);
        bubbleUpEvents(scope, withScope);

        ContinueFlowElementTriggerDTO continueTrigger =
            new ContinueFlowElementTriggerDTO(
                processInstanceProcessingContext.getProcessInstance().getProcessInstanceId(),
                trigger.getElementInstanceIdPath(),
                null,
                VariablesDTO.empty());
        scope
            .getDirectInstanceResult()
            .addContinueInstance(
                new ContinueFlowNodeInstanceInfo(
                    instanceWithInstanceId, continueTrigger, childVariableScope));

        doBusiness(processInstanceProcessingContext, scope, variableScope);
      }
    } else {
      if (!trigger.getElementInstanceIdPath().isEmpty()) {
        FlowNodeInstance<?> flowNodeInstance =
            scope
                .getFlowNodeInstances()
                .getInstanceWithInstanceId(
                    trigger.getElementInstanceIdPath().get(scope.getSubProcessLevel()));
        scope.getDirectInstanceResult().addAbortInstance(flowNodeInstance);
      } else {
        Map<Long, FlowNodeInstance<?>> allInstances =
            scope.getFlowNodeInstances().getAllInstances();
        allInstances
            .values()
            .forEach(
                flowNodeInstance ->
                    scope.getDirectInstanceResult().addAbortInstance(flowNodeInstance));
        scope.setState(ExecutionState.ABORTED);
      }
      doBusiness(processInstanceProcessingContext, scope, variableScope);
    }
    return null;
  }

  public void doBusiness(
      ProcessInstanceProcessingContext processInstanceProcessingContext,
      Scope scope,
      VariableScope variableScope) {

    DirectInstanceResult directInstanceResult = scope.getDirectInstanceResult();
    while (directInstanceResult.hasDirectTriggers()) {
      if (directInstanceResult.isAbortScope()) {
        log.info("Aborting scope {}", scope.getParentFlowNodeInstance());
        abortScope(scope);
        directInstanceResult.resetAbortScope();
      }
      processEvents(processInstanceProcessingContext, scope, variableScope);
      processAbortInstances(processInstanceProcessingContext, scope, variableScope);
      processNewInstances(processInstanceProcessingContext, scope);
      processContinueInstances(processInstanceProcessingContext, scope);
    }
    scope.updateActiveCountForInstances();

    terminateEventSubprocessSubscriptionsIfDone(processInstanceProcessingContext, scope);
  }

  private void abortScope(Scope scope) {
    scope
        .getFlowNodeInstances()
        .getAllInstances()
        .values()
        .forEach(
            instance -> {
              if (instance.isActive()) {
                scope.getDirectInstanceResult().addAbortInstance(instance);
              }
            });
    scope.setState(ExecutionState.ABORTED);
  }

  public void bubbleUpEvents(Scope scope, WithScope withScope) {
    EventSignal bubbleUpEventSignal =
        withScope.getScope().getDirectInstanceResult().pollBubbleUpEvent();
    while (bubbleUpEventSignal != null) {
      bubbleUpEventSignal.bubbleUp();
      scope.getDirectInstanceResult().addEvent(bubbleUpEventSignal);
      bubbleUpEventSignal = withScope.getScope().getDirectInstanceResult().pollBubbleUpEvent();
    }
  }

  private void processEvents(
      ProcessInstanceProcessingContext processInstanceProcessingContext,
      Scope scope,
      VariableScope variableScope) {

    EventSignal eventSignal = scope.getDirectInstanceResult().pollEvent();
    while (eventSignal != null) {
      processEventByFlowNodeInstance(
          processInstanceProcessingContext,
          scope,
          variableScope,
          eventSignal,
          eventSignal.getCurrentInstance());
      eventSignal = scope.getDirectInstanceResult().pollEvent();
    }
  }

  private void processNewInstances(
      ProcessInstanceProcessingContext processInstanceProcessingContext, Scope scope) {

    StartFlowNodeInstanceInfo instanceInfo =
        scope.getDirectInstanceResult().pollNewFlowNodeInstance();
    while (instanceInfo != null) {
      FlowNodeInstance<?> fLowNodeInstance = instanceInfo.flowNodeInstance();
      FlowNodeInstanceProcessor<?, ?, ?> processor =
          flowNodeInstanceProcessorProvider.getProcessor(
              fLowNodeInstance.getFlowNode(), fLowNodeInstance.isIteration());
      processor.processStart(
          processInstanceProcessingContext,
          scope,
          instanceInfo.variableScope(),
          fLowNodeInstance,
          instanceInfo.inputSequenceFlowId());

      if (fLowNodeInstance.isActive()) {
        startAttachedBoundaryEvents(scope, instanceInfo.variableScope(), fLowNodeInstance);
      }

      instanceInfo = scope.getDirectInstanceResult().pollNewFlowNodeInstance();
    }
  }

  private void processContinueInstances(
      ProcessInstanceProcessingContext processInstanceProcessingContext, Scope scope) {

    ContinueFlowNodeInstanceInfo continueInstance =
        scope.getDirectInstanceResult().pollContinueInstance();
    while (continueInstance != null) {
      FlowNodeInstance<?> flowNodeInstance = continueInstance.flowNodeInstance();
      VariableScope variableScope = continueInstance.variableScope();

      if (flowNodeInstance instanceof IntermediateCatchEventInstance intermediateCatchEventInstance
          && intermediateCatchEventInstance.isActive()) {
        Optional<EventBasedGateway> eventBasedGateway =
            flowNodeInstance.getFlowNode().getIncomingSequenceFlows().stream()
                .filter(flow -> flow.getSourceNode() instanceof EventBasedGateway)
                .map(flow -> (EventBasedGateway) flow.getSourceNode())
                .findFirst();
        if (eventBasedGateway.isPresent()) {
          Long gatewayInstanceId = scope.getGatewayInstanceId(eventBasedGateway.get().getId());
          if (gatewayInstanceId != null) {
            EventBasedGatewayInstance gatewayInstance =
                (EventBasedGatewayInstance)
                    scope.getFlowNodeInstances().getInstanceWithInstanceId(gatewayInstanceId);
            if (gatewayInstance != null) {
              for (Long connectedInstanceId : gatewayInstance.getConnectedFlowNodeInstanceIds()) {
                if (connectedInstanceId != flowNodeInstance.getElementInstanceId()) {
                  FlowNodeInstance<?> connectedInstance =
                      scope.getFlowNodeInstances().getInstanceWithInstanceId(connectedInstanceId);
                  if (connectedInstance != null && connectedInstance.isActive()) {
                    scope.getDirectInstanceResult().addAbortInstance(connectedInstance);
                  }
                }
              }
            }
          }
        }
      }

      if (flowNodeInstance instanceof BoundaryEventInstance boundaryEventInstance
          && boundaryEventInstance.getFlowNode().isCancelActivity()) {
        Long activityInstanceId =
            scope.getBoundaryEventToActivity().get(boundaryEventInstance.getElementInstanceId());
        if (activityInstanceId != null) {
          FlowNodeInstance<?> activityInstanceToAbort =
              scope.getFlowNodeInstances().getInstanceWithInstanceId(activityInstanceId);
          if (activityInstanceToAbort != null && activityInstanceToAbort.isActive()) {
            scope.getDirectInstanceResult().addAbortInstance(activityInstanceToAbort);
          }
        }
      }

      FlowNodeInstanceProcessor<?, ?, ?> processor =
          flowNodeInstanceProcessorProvider.getProcessor(
              flowNodeInstance.getFlowNode(), flowNodeInstance.isIteration());

      processor.processContinue(
          processInstanceProcessingContext,
          scope,
          continueInstance.variableScope(),
          flowNodeInstance,
          continueInstance.trigger());

      if (flowNodeInstance.isCmpleted()) {
        abortBoundaryEvents(
            processInstanceProcessingContext, scope, variableScope, flowNodeInstance);
      }

      continueInstance = scope.getDirectInstanceResult().pollContinueInstance();
    }
  }

  private void processAbortInstances(
      ProcessInstanceProcessingContext processInstanceProcessingContext,
      Scope scope,
      VariableScope variableScope) {

    FlowNodeInstance<?> abortInstance = scope.getDirectInstanceResult().pollAbortInstance();
    while (abortInstance != null) {

      FlowNodeInstanceProcessor<?, ?, ?> processor =
          flowNodeInstanceProcessorProvider.getProcessor(
              abortInstance.getFlowNode(), abortInstance.isIteration());

      VariableScope childVariableScope = variableScope.selectChildScope(abortInstance);
      processor.processAbort(
          processInstanceProcessingContext, scope, childVariableScope, abortInstance);

      abortBoundaryEvents(processInstanceProcessingContext, scope, variableScope, abortInstance);

      abortInstance = scope.getDirectInstanceResult().pollAbortInstance();
    }
  }

  private void abortBoundaryEvents(
      ProcessInstanceProcessingContext processInstanceProcessingContext,
      Scope scope,
      VariableScope variableScope,
      FlowNodeInstance<?> flowNodeInstance) {
    Set<Long> boundaryEvents =
        scope
            .getActivityToBoundaryEvents()
            .getOrDefault(flowNodeInstance.getElementInstanceId(), Collections.emptySet());

    boundaryEvents.forEach(
        boundaryEventId -> {
          FlowNodeInstance<?> boundaryEventInstance =
              scope.getFlowNodeInstances().getInstanceWithInstanceId(boundaryEventId);
          VariableScope childVariableScope = variableScope.selectChildScope(boundaryEventInstance);
          boundaryEventProcessor.processAbort(
              processInstanceProcessingContext, scope, childVariableScope, boundaryEventInstance);
        });
  }

  private void startAttachedBoundaryEvents(
      Scope scope, VariableScope parentScope, FlowNodeInstance<?> fLowNodeInstance) {
    if (fLowNodeInstance instanceof ActivityInstance<?> activityInstance) {
      Activity activity = activityInstance.getFlowNode();
      activity
          .getBoundaryEvents()
          .forEach(
              boundaryEvent -> {
                FlowNodeInstance<?> boundaryEventInstance =
                    boundaryEvent.createAndStoreNewInstance(
                        fLowNodeInstance.getParentInstance(), scope);

                scope
                    .getActivityToBoundaryEvents()
                    .computeIfAbsent(
                        activityInstance.getElementInstanceId(), _ -> new java.util.HashSet<>())
                    .add(boundaryEventInstance.getElementInstanceId());
                scope
                    .getBoundaryEventToActivity()
                    .put(
                        boundaryEventInstance.getElementInstanceId(),
                        activityInstance.getElementInstanceId());

                VariableScope childVariableScope =
                    parentScope.selectChildScope(boundaryEventInstance);
                scope
                    .getDirectInstanceResult()
                    .addNewFlowNodeInstance(
                        new StartFlowNodeInstanceInfo(
                            boundaryEventInstance, null, childVariableScope));
              });
    }
  }

  private static StartFlowNodeInstanceInfo createNewInstanceAndAddToDirectInstanceResult(
      Scope scope, String elementId, VariableScope parentVariableScope, VariablesDTO variables) {
    FlowNode flowNode = scope.getFlowElements().getStartNode(elementId);

    FlowNodeInstance<?> flowNodeInstance =
        flowNode.createAndStoreNewInstance(scope.getParentFlowNodeInstance(), scope);

    VariableScope childVariableScope = parentVariableScope.selectChildScope(flowNodeInstance);
    childVariableScope.merge(variables);
    DirectInstanceResult directInstanceResult = scope.getDirectInstanceResult();

    StartFlowNodeInstanceInfo startFlowNodeInstanceInfo =
        new StartFlowNodeInstanceInfo(flowNodeInstance, null, childVariableScope);

    directInstanceResult.addNewFlowNodeInstance(startFlowNodeInstanceInfo);
    return startFlowNodeInstanceInfo;
  }

  private void processEventByFlowNodeInstance(
      ProcessInstanceProcessingContext processInstanceProcessingContext,
      Scope scope,
      VariableScope variableScope,
      EventSignal event,
      FlowNodeInstance<?> fLowNodeInstance) {
    long elementInstanceId = event.getCurrentInstance().getElementInstanceId();

    boolean eventHandled =
        processEventsByBoundaryEvents(
            processInstanceProcessingContext, scope, variableScope, event, elementInstanceId);

    if (!eventHandled) {
      // Not handled by boundary event, check event subprocesses
      eventHandled = processEventByEventSubprocesses(scope, variableScope, event, fLowNodeInstance);
    }

    if (!eventHandled) {
      scope.getDirectInstanceResult().addBubbleUpEvent(event);
    }
  }

  private static boolean processEventByEventSubprocesses(
      Scope scope,
      VariableScope variableScope,
      EventSignal event,
      FlowNodeInstance<?> fLowNodeInstance) {
    boolean eventHandled = false;
    // First check any event subprocesses which are able to handle this event
    // First do a round for specific event codes
    List<SubProcess> eventTriggeredSubProcesses =
        scope.getFlowElements().getEventTriggeredSubProcesses();
    for (SubProcess eventSsubProcess : eventTriggeredSubProcesses) {
      // Check if the event originated from within this event subprocess to prevent infinite loops
      // This applies to all event types: errors, escalations, signals, etc.
      if (eventOriginatesFromSubProcess(event, eventSsubProcess)) {
        continue; // Skip this subprocess - let the event bubble up further
      }

      List<StartEvent> startEvents = eventSsubProcess.getElements().getStartEvents();
      for (StartEvent startEvent : startEvents) {
        Set<EventDefinition> eventDefinitions = startEvent.getEventDefinitions();
        for (EventDefinition eventDefinition : eventDefinitions) {
          if (eventDefinition.handlesEvent(event)) {
            // First terminate any active elements
            if (startEvent.isInterrupting()) {
              Map<Long, FlowNodeInstance<?>> allInstances =
                  scope.getFlowNodeInstances().getAllInstances();
              for (FlowNodeInstance<?> instance : allInstances.values()) {
                if (instance.isActive()) {
                  scope.getDirectInstanceResult().addAbortInstance(instance);
                }
              }
            }
            // Create a new instanceToContinue for the event subprocess
            FlowNodeInstance<?> eventSubProcessInstance =
                eventSsubProcess.createAndStoreNewInstance(
                    fLowNodeInstance.getParentInstance(), scope);
            VariableScope childVariableScope =
                variableScope.selectChildScope(eventSubProcessInstance);
            StartFlowNodeInstanceInfo startFlowNodeInstanceInfo =
                new StartFlowNodeInstanceInfo(eventSubProcessInstance, null, childVariableScope);
            scope.getDirectInstanceResult().addNewFlowNodeInstance(startFlowNodeInstanceInfo);

            if (startEvent.isInterrupting()) {
              scope.getDirectInstanceResult().addAbortInstance(event.getCurrentInstance());
            }
            eventHandled = true;
          }
          if (eventHandled) {
            break;
          }
        }
        if (eventHandled) {
          break;
        }
      }
    }

    // Now for catch all events
    if (!eventHandled) {
      for (SubProcess eventSsubProcess : eventTriggeredSubProcesses) {
        // Check if the event originated from within this event subprocess to prevent infinite loops
        // This applies to all event types: errors, escalations, signals, etc.
        if (eventOriginatesFromSubProcess(event, eventSsubProcess)) {
          continue; // Skip this subprocess - let the event bubble up further
        }

        List<StartEvent> startEvents = eventSsubProcess.getElements().getStartEvents();
        for (StartEvent startEvent : startEvents) {
          Set<EventDefinition> eventDefinitions = startEvent.getEventDefinitions();
          for (EventDefinition eventDefinition : eventDefinitions) {
            if (eventDefinition.handlesEventCatchAll(event)) {
              // First terminate any active elements
              if (startEvent.isInterrupting()) {
                Map<Long, FlowNodeInstance<?>> allInstances =
                    scope.getFlowNodeInstances().getAllInstances();
                for (FlowNodeInstance<?> instance : allInstances.values()) {
                  if (instance.isActive()) {
                    scope.getDirectInstanceResult().addAbortInstance(instance);
                  }
                }
              }
              // Create a new instanceToContinue for the event subprocess
              FlowNodeInstance<?> eventSubProcessInstance =
                  eventSsubProcess.createAndStoreNewInstance(
                      fLowNodeInstance.getParentInstance(), scope);
              VariableScope childVariableScope =
                  variableScope.selectChildScope(eventSubProcessInstance);
              StartFlowNodeInstanceInfo startFlowNodeInstanceInfo =
                  new StartFlowNodeInstanceInfo(eventSubProcessInstance, null, childVariableScope);
              scope.getDirectInstanceResult().addNewFlowNodeInstance(startFlowNodeInstanceInfo);
              eventHandled = true;
            }
            if (eventHandled) {
              break;
            }
          }
          if (eventHandled) {
            break;
          }
        }
      }
    }
    return eventHandled;
  }

  /**
   * Checks if an event originated from within a specific event subprocess. This prevents infinite
   * loops where an event subprocess triggers an event that would trigger itself again.
   *
   * <p>Examples of scenarios this prevents:
   *
   * <ul>
   *   <li>Error Event Subprocess throws an error → would match itself
   *   <li>Escalation Event Subprocess throws an escalation → would match itself
   *   <li>Signal Event Subprocess throws a signal → would match itself
   * </ul>
   *
   * <p>Works for both interrupting and non-interrupting event subprocesses.
   *
   * @param event The event signal (error, escalation, signal, message, etc.)
   * @param eventSubProcess The event subprocess to check
   * @return true if the event originated from within the event subprocess
   */
  private static boolean eventOriginatesFromSubProcess(
      EventSignal event, SubProcess eventSubProcess) {
    // Check the path to source to see if any element in the path is this event subprocess
    for (FlowNodeInstance<?> instanceInPath : event.getPathToSource()) {
      if (instanceInPath instanceof SubProcessInstance subProcessInstance) {
        // Check if this subprocess instance is an instance of the event subprocess we're checking
        if (subProcessInstance.getFlowNode().getId().equals(eventSubProcess.getId())) {
          return true; // Event originated from within this event subprocess
        }
      }
    }
    return false;
  }

  private boolean processEventsByBoundaryEvents(
      ProcessInstanceProcessingContext processInstanceProcessingContext,
      Scope scope,
      VariableScope variableScope,
      EventSignal event,
      long elementInstanceId) {
    Long boundaryEventThatHandledEvent = null;
    Set<Long> boundaryEventIdsForInstance =
        scope.getActivityToBoundaryEvents().getOrDefault(elementInstanceId, Collections.emptySet());
    DirectInstanceResult directInstanceResult = scope.getDirectInstanceResult();
    for (Long boundaryEventId : boundaryEventIdsForInstance) {
      BoundaryEventInstance boundaryEventInstance =
          (BoundaryEventInstance)
              scope.getFlowNodeInstances().getInstanceWithInstanceId(boundaryEventId);
      VariableScope childVariableScope = variableScope.selectChildScope(boundaryEventInstance);
      boolean eventHandled =
          boundaryEventProcessor.processEvent(
              processInstanceProcessingContext,
              scope,
              childVariableScope,
              boundaryEventInstance,
              event);
      if (eventHandled) {
        boundaryEventThatHandledEvent = boundaryEventInstance.getElementInstanceId();
        if (boundaryEventInstance.getFlowNode().isCancelActivity()) {
          directInstanceResult.addAbortInstance(event.getCurrentInstance());
        }
        break;
      }
    }

    if (boundaryEventThatHandledEvent == null) {
      // If not handled by specific codes, check for catch all
      for (Long boundaryEventId : boundaryEventIdsForInstance) {
        BoundaryEventInstance boundaryEventInstance =
            (BoundaryEventInstance)
                scope.getFlowNodeInstances().getInstanceWithInstanceId(boundaryEventId);
        VariableScope childVariableScope = variableScope.selectChildScope(boundaryEventInstance);
        boolean eventHandled =
            boundaryEventProcessor.processEventCatchAll(
                processInstanceProcessingContext,
                scope,
                childVariableScope,
                boundaryEventInstance,
                event);
        if (eventHandled) {
          boundaryEventThatHandledEvent = boundaryEventInstance.getElementInstanceId();
          if (boundaryEventInstance.getFlowNode().isCancelActivity()) {
            directInstanceResult.addAbortInstance(event.getCurrentInstance());
          }
          break;
        }
      }
    }

    if (boundaryEventThatHandledEvent != null) {
      BoundaryEventInstance boundaryEventInstanceThatHandledEvent =
          (BoundaryEventInstance)
              scope.getFlowNodeInstances().getInstanceWithInstanceId(boundaryEventThatHandledEvent);
      if (boundaryEventInstanceThatHandledEvent.getFlowNode().isCancelActivity()) {
        Set<Long> boundaryEventsForInstance =
            scope
                .getActivityToBoundaryEvents()
                .get(event.getCurrentInstance().getElementInstanceId());
        for (Long boundaryEventId : boundaryEventsForInstance) {
          if (!boundaryEventId.equals(boundaryEventThatHandledEvent)) {
            VariableScope childVariableScope =
                variableScope.selectChildScope(boundaryEventInstanceThatHandledEvent);
            boundaryEventProcessor.processAbort(
                processInstanceProcessingContext,
                scope,
                childVariableScope,
                boundaryEventInstanceThatHandledEvent);
          }
        }
      }
    }
    return boundaryEventThatHandledEvent != null;
  }

  private void startSubscriptionsForEventSubprocesses(
      ProcessInstanceProcessingContext processInstanceProcessingContext,
      Scope scope,
      VariableScope variableScope) {
    // first check if we need to start timer triggers for event subprocesses with corresponding
    // timer start events
    List<SubProcess> eventTriggeredSubProcesses =
        scope.getFlowElements().getEventTriggeredSubProcesses();
    for (SubProcess eventTriggeredSubProcess : eventTriggeredSubProcesses) {
      List<StartEvent> startEvents = eventTriggeredSubProcess.getElements().getStartEvents();
      for (StartEvent startEvent : startEvents) {
        Optional<TimerEventDefinition> optTimerEventDefinition =
            startEvent.getTimerEventDefinition();
        if (optTimerEventDefinition.isPresent()) {
          TimerEventDefinition timerEventDefinition = optTimerEventDefinition.get();
          ScheduledStartInfo scheduledStartInfo =
              new ScheduledStartInfo(scope, eventTriggeredSubProcess, timerEventDefinition);
          processInstanceProcessingContext
              .getInstanceResult()
              .addScheduledStart(scheduledStartInfo);
        }

        Optional<MessageEventDefinition> optMessageventDefinition =
            startEvent.getMessageventDefinition();
        if (optMessageventDefinition.isPresent()) {
          MessageEventDefinition messageEventDefinition = optMessageventDefinition.get();

          Message referencedMessage = messageEventDefinition.getReferencedMessage();
          if (referencedMessage == null) {
            throw new ProcessingException(
                "Message event definition "
                    + messageEventDefinition.getId()
                    + " has no referenced message");
          }
          JsonNode jsonNode =
              feelExpressionHandler.processFeelExpression(
                  referencedMessage.correlationKey(), variableScope);
          if (jsonNode == null || jsonNode.isNull()) {
            throw new ProcessingException("Correlation key expression returned null");
          }
          String correlationKey = jsonNode.asText();

          String messageName = referencedMessage.name();
          NewCorrelationSubscriptionMessageEventInfo messageSubscription =
              new NewCorrelationSubscriptionMessageEventInfo(
                  messageName,
                  correlationKey,
                  (FlowNodeInstance<?>) scope.getParentFlowNodeInstance(),
                  eventTriggeredSubProcess);

          scope.addMessageSubscription(messageName, correlationKey);

          processInstanceProcessingContext
              .getInstanceResult()
              .addNewCorrelationSubcriptionMessageEvent(messageSubscription);
        }
      }
    }
  }

  private static void terminateEventSubprocessSubscriptionsIfDone(
      ProcessInstanceProcessingContext processInstanceProcessingContext, Scope scope) {
    if (scope.getState().isDone()) {
      scope
          .getMessageSubscriptions()
          .forEach(
              (messageName, correlationKeys) ->
                  correlationKeys.forEach(
                      correlationKey -> {
                        TerminateCorrelationSubscriptionMessageEventInfo messageEventInfo =
                            new TerminateCorrelationSubscriptionMessageEventInfo(
                                messageName, correlationKey);
                        processInstanceProcessingContext
                            .getInstanceResult()
                            .addTerminateCorrelationSubscriptionMessageEvent(messageEventInfo);
                      }));

      scope
          .getScheduleKeys()
          .forEach(
              scheduleKey ->
                  processInstanceProcessingContext.getInstanceResult().cancelSchedule(scheduleKey));
    }
  }

  public Void processSetVariables(
      List<Long> parentElementInstanceIdPath,
      VariablesDTO variables,
      ProcessInstanceProcessingContext processInstanceProcessingContext,
      Scope scope,
      VariableScope variableScope) {

    int subProcessLevel = scope.getSubProcessLevel();

    if (subProcessLevel < parentElementInstanceIdPath.size() - 1) {
      FlowNodeInstance<?> instanceWithInstanceId =
          scope
              .getFlowNodeInstances()
              .getInstanceWithInstanceId(parentElementInstanceIdPath.get(subProcessLevel));
      if (instanceWithInstanceId == null) {
        // Element should be there. We can't start an element in a subprocess which is not there
        throw new IllegalArgumentException(
            "No element with instance id "
                + parentElementInstanceIdPath.get(subProcessLevel)
                + " found on level "
                + subProcessLevel);
      }

      if (instanceWithInstanceId instanceof WithScope withScope) {
        VariableScope childVariableScope = variableScope.selectChildScope(instanceWithInstanceId);
        processSetVariables(
            parentElementInstanceIdPath,
            variables,
            processInstanceProcessingContext,
            withScope.getScope(),
            childVariableScope);

      } else {
        throw new IllegalArgumentException(
            "Element with instance id "
                + parentElementInstanceIdPath.get(subProcessLevel)
                + " is not a scope");
      }
    } else if (subProcessLevel == (parentElementInstanceIdPath.size() - 1)
        && !parentElementInstanceIdPath.isEmpty()) {
      FlowNodeInstance<?> flowNodeInstance =
          scope
              .getFlowNodeInstances()
              .getInstanceWithInstanceId(
                  parentElementInstanceIdPath.get(scope.getSubProcessLevel()));
      VariableScope childVariableScope = variableScope.selectChildScope(flowNodeInstance);

      FlowNodeInstanceProcessor<?, ?, ?> processor =
          flowNodeInstanceProcessorProvider.getProcessor(
              flowNodeInstance.getFlowNode(), flowNodeInstance.isIteration());

      processor.processSetVariables(
          processInstanceProcessingContext, scope, flowNodeInstance, childVariableScope, variables);

    } else if (parentElementInstanceIdPath.isEmpty()) {
      variableScope.merge(variables);
    } else {
      throw new IllegalArgumentException(
          "Subprocess level "
              + subProcessLevel
              + " is out of bounds for parentElementInstanceIdPath "
              + parentElementInstanceIdPath);
    }
    return null;
  }
}
