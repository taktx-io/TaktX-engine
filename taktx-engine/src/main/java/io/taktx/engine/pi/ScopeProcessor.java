/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks All rights reserved.
 * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 * Free use is permitted with up to 3 Kafka partitions per topic. See LICENSE file for details.
 * For commercial use or more partitions and features, contact [https://www.taktx.io/contact].
 */

package io.taktx.engine.pi;

import io.taktx.dto.AbortTriggerDTO;
import io.taktx.dto.ContinueFlowElementTriggerDTO;
import io.taktx.dto.EventSignalDTO;
import io.taktx.dto.EventSignalTriggerDTO;
import io.taktx.dto.ExecutionState;
import io.taktx.dto.VariablesDTO;
import io.taktx.engine.feel.FeelExpressionHandler;
import io.taktx.engine.pd.model.Activity;
import io.taktx.engine.pd.model.EventDefinition;
import io.taktx.engine.pd.model.EventSignal;
import io.taktx.engine.pd.model.FlowNode;
import io.taktx.engine.pd.model.MessageEventDefinition;
import io.taktx.engine.pd.model.StartEvent;
import io.taktx.engine.pd.model.SubProcess;
import io.taktx.engine.pd.model.TimerEventDefinition;
import io.taktx.engine.pi.model.ActivityInstance;
import io.taktx.engine.pi.model.BoundaryEventInstance;
import io.taktx.engine.pi.model.ContinueFlowNodeInstanceInfo;
import io.taktx.engine.pi.model.FlowNodeInstance;
import io.taktx.engine.pi.model.NewCorrelationSubscriptionMessageEventInfo;
import io.taktx.engine.pi.model.ScheduledStartInfo;
import io.taktx.engine.pi.model.Scope;
import io.taktx.engine.pi.model.StartFlowNodeInstanceInfo;
import io.taktx.engine.pi.model.SubProcessInstance;
import io.taktx.engine.pi.model.TerminateCorrelationSubscriptionMessageEventInfo;
import io.taktx.engine.pi.model.WithScope;
import io.taktx.engine.pi.processor.BoundaryEventInstanceProcessor;
import io.taktx.engine.pi.processor.FlowNodeInstanceProcessor;
import io.taktx.engine.pi.processor.FlowNodeInstanceProcessorProvider;
import jakarta.enterprise.context.ApplicationScoped;
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

  public void processStart(
      List<Long> parentElementInstanceIdPath,
      String elementId,
      VariablesDTO variables,
      ProcessInstanceProcessingContext processInstanceProcessingContext,
      Scope scope) {

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
        processStart(
            parentElementInstanceIdPath,
            elementId,
            variables,
            processInstanceProcessingContext,
            withScope.getScope());

        bubbleUpEvents(scope, withScope);

        ContinueFlowElementTriggerDTO trigger =
            new ContinueFlowElementTriggerDTO(
                processInstanceProcessingContext.getProcessInstance().getProcessInstanceId(),
                parentElementInstanceIdPath,
                null,
                VariablesDTO.empty());
        scope
            .getDirectInstanceResult()
            .addContinueInstance(new ContinueFlowNodeInstanceInfo(instanceWithInstanceId, trigger));
        doBusiness(processInstanceProcessingContext, scope);
      } else {
        throw new IllegalArgumentException(
            "Element with instance id "
                + parentElementInstanceIdPath.get(subProcessLevel)
                + " is not a scope");
      }
    } else if (subProcessLevel == parentElementInstanceIdPath.size()) {

      if (scope.getActiveCnt() == 0) {
        startSubscriptionsForEventSubprocesses(processInstanceProcessingContext, scope);
      }

      scope.getVariableScope().merge(variables);

      FlowNodeInstance<?> newInstance =
          createNewInstanceAndAddToDirectInstanceResult(
              processInstanceProcessingContext, scope, elementId);

      if (newInstance instanceof SubProcessInstance subProcessInstance
          && subProcessInstance.getFlowNode().isTriggeredByEvent()) {
        FlowNode startNode = subProcessInstance.getFlowElements().getStartNode(null);
        if (startNode instanceof StartEvent startEvent && startEvent.isInterrupting()) {
          Map<Long, FlowNodeInstance<?>> allInstances =
              scope.getFlowNodeInstances().getAllInstances();
          for (FlowNodeInstance<?> instance : allInstances.values()) {
            if (instance.isActive()
                && instance.getElementInstanceId() != newInstance.getElementInstanceId()) {
              scope.getDirectInstanceResult().addAbortInstance(instance);
            }
          }
        }
      }
      doBusiness(processInstanceProcessingContext, scope);
    } else {
      throw new IllegalArgumentException(
          "Subprocess level "
              + subProcessLevel
              + " is out of bounds for parentElementInstanceIdPath "
              + parentElementInstanceIdPath);
    }
  }

  public void processContinue(
      ProcessInstanceProcessingContext processInstanceProcessingContext,
      Scope scope,
      ContinueFlowElementTriggerDTO trigger,
      List<Long> elementInstanceIdPath) {

    int subProcessLevel = scope.getSubProcessLevel();
    if (subProcessLevel < elementInstanceIdPath.size() - 1) {
      FlowNodeInstance<?> instanceWithInstanceId =
          scope
              .getFlowNodeInstances()
              .getInstanceWithInstanceId(elementInstanceIdPath.get(subProcessLevel));
      if (instanceWithInstanceId instanceof WithScope withScope) {
        processContinue(
            processInstanceProcessingContext, withScope.getScope(), trigger, elementInstanceIdPath);

        bubbleUpEvents(scope, withScope);

        scope
            .getDirectInstanceResult()
            .addContinueInstance(new ContinueFlowNodeInstanceInfo(instanceWithInstanceId, trigger));

        doBusiness(processInstanceProcessingContext, scope);
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

      scope.getVariableScope().merge(trigger.getVariables());

      if (trigger instanceof EventSignalTriggerDTO eventSignalTriggerDTO) {
        for (EventSignalDTO eventSignalDTO : eventSignalTriggerDTO.getEventSignalList()) {
          EventSignal eventSignal = dtoMapper.map(eventSignalDTO);
          eventSignal.getPathToSource().addFirst(flowNodeInstance);
          scope.getDirectInstanceResult().addEvent(eventSignal);
        }

      } else {
        ContinueFlowNodeInstanceInfo continueInstance =
            new ContinueFlowNodeInstanceInfo(flowNodeInstance, trigger);

        scope.getDirectInstanceResult().addContinueInstance(continueInstance);
      }
      doBusiness(processInstanceProcessingContext, scope);
    }
  }

  public void processAbort(
      ProcessInstanceProcessingContext processInstanceProcessingContext,
      Scope scope,
      AbortTriggerDTO trigger) {
    int subProcessLevel = scope.getSubProcessLevel();

    if (subProcessLevel < trigger.getElementInstanceIdPath().size() - 1) {
      FlowNodeInstance<?> instanceWithInstanceId =
          scope
              .getFlowNodeInstances()
              .getInstanceWithInstanceId(trigger.getElementInstanceIdPath().get(subProcessLevel));
      if (instanceWithInstanceId instanceof WithScope withScope) {
        processAbort(processInstanceProcessingContext, withScope.getScope(), trigger);

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
                new ContinueFlowNodeInstanceInfo(instanceWithInstanceId, continueTrigger));

        doBusiness(processInstanceProcessingContext, scope);
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
      doBusiness(processInstanceProcessingContext, scope);
    }
  }

  public void doBusiness(
      ProcessInstanceProcessingContext processInstanceProcessingContext, Scope scope) {

    DirectInstanceResult directInstanceResult = scope.getDirectInstanceResult();
    while (directInstanceResult.hasDirectTriggers()) {
      if (directInstanceResult.isAbortScope()) {
        log.info("Aborting scope {}", scope.getParentFlowNodeInstance());
        abortScope(scope);
        directInstanceResult.resetAbortScope();
      }
      processEvents(processInstanceProcessingContext, scope);
      processAbortInstances(processInstanceProcessingContext, scope);
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

  private static void bubbleUpEvents(Scope scope, WithScope withScope) {
    EventSignal bubbleUpEventSignal =
        withScope.getScope().getDirectInstanceResult().pollBubbleUpEvent();
    while (bubbleUpEventSignal != null) {
      bubbleUpEventSignal.bubbleUp();
      scope.getDirectInstanceResult().addEvent(bubbleUpEventSignal);
      bubbleUpEventSignal = withScope.getScope().getDirectInstanceResult().pollBubbleUpEvent();
    }
  }

  private void processEvents(
      ProcessInstanceProcessingContext processInstanceProcessingContext, Scope scope) {

    EventSignal eventSignal = scope.getDirectInstanceResult().pollEvent();
    while (eventSignal != null) {
      processEventByFlowNodeInstance(
          processInstanceProcessingContext, scope, eventSignal, eventSignal.getCurrentInstance());
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
          fLowNodeInstance,
          instanceInfo.inputSequenceFlowId());

      if (fLowNodeInstance.isActive()) {
        startAttachedBoundaryEvents(processInstanceProcessingContext, scope, fLowNodeInstance);
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
          processInstanceProcessingContext, scope, flowNodeInstance, continueInstance.trigger());

      if (flowNodeInstance.isCmpleted()) {
        abortBoundaryEvents(processInstanceProcessingContext, scope, flowNodeInstance);
      }

      continueInstance = scope.getDirectInstanceResult().pollContinueInstance();
    }
  }

  private void processAbortInstances(
      ProcessInstanceProcessingContext processInstanceProcessingContext, Scope scope) {

    FlowNodeInstance<?> abortInstance = scope.getDirectInstanceResult().pollAbortInstance();
    while (abortInstance != null) {

      FlowNodeInstanceProcessor<?, ?, ?> processor =
          flowNodeInstanceProcessorProvider.getProcessor(
              abortInstance.getFlowNode(), abortInstance.isIteration());

      processor.processAbort(processInstanceProcessingContext, scope, abortInstance);

      abortBoundaryEvents(processInstanceProcessingContext, scope, abortInstance);

      abortInstance = scope.getDirectInstanceResult().pollAbortInstance();
    }
  }

  private void abortBoundaryEvents(
      ProcessInstanceProcessingContext processInstanceProcessingContext,
      Scope scope,
      FlowNodeInstance<?> abortInstance) {
    Set<Long> boundaryEvents =
        scope
            .getActivityToBoundaryEvents()
            .getOrDefault(abortInstance.getElementInstanceId(), Collections.emptySet());

    boundaryEvents.forEach(
        boundaryEventId -> {
          FlowNodeInstance<?> boundaryEventInstance =
              scope.getFlowNodeInstances().getInstanceWithInstanceId(boundaryEventId);
          boundaryEventProcessor.processAbort(
              processInstanceProcessingContext, scope, boundaryEventInstance);
        });
  }

  private void startAttachedBoundaryEvents(
      ProcessInstanceProcessingContext processInstanceProcessingContext,
      Scope scope,
      FlowNodeInstance<?> fLowNodeInstance) {
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

                scope
                    .getDirectInstanceResult()
                    .addNewFlowNodeInstance(
                        processInstanceProcessingContext.getProcessInstance(),
                        new StartFlowNodeInstanceInfo(boundaryEventInstance, null));
              });
    }
  }

  private static FlowNodeInstance<?> createNewInstanceAndAddToDirectInstanceResult(
      ProcessInstanceProcessingContext processInstanceProcessingContext,
      Scope scope,
      String elementId) {
    FlowNode flowNode = scope.getFlowElements().getStartNode(elementId);

    FlowNodeInstance<?> flowNodeInstance =
        flowNode.createAndStoreNewInstance(scope.getParentFlowNodeInstance(), scope);

    DirectInstanceResult directInstanceResult = scope.getDirectInstanceResult();

    StartFlowNodeInstanceInfo startFlowNodeInstanceInfo =
        new StartFlowNodeInstanceInfo(flowNodeInstance, null);

    directInstanceResult.addNewFlowNodeInstance(
        processInstanceProcessingContext.getProcessInstance(), startFlowNodeInstanceInfo);
    return flowNodeInstance;
  }

  private void processEventByFlowNodeInstance(
      ProcessInstanceProcessingContext processInstanceProcessingContext,
      Scope scope,
      EventSignal event,
      FlowNodeInstance<?> fLowNodeInstance) {
    long elementInstanceId = event.getCurrentInstance().getElementInstanceId();

    boolean eventHandled =
        processEventsByBoundaryEvents(
            processInstanceProcessingContext, scope, event, elementInstanceId);

    if (!eventHandled) {
      // Not handled by boundary event, check event subprocesses
      eventHandled =
          processEventByEventSubprocesses(
              processInstanceProcessingContext, scope, event, fLowNodeInstance, eventHandled);
    }

    if (!eventHandled) {
      scope.getDirectInstanceResult().addBubbleUpEvent(event);
    }
  }

  private static boolean processEventByEventSubprocesses(
      ProcessInstanceProcessingContext processInstanceProcessingContext,
      Scope scope,
      EventSignal event,
      FlowNodeInstance<?> fLowNodeInstance,
      boolean eventHandled) {
    // First check any event subprocesses which are able to handle this event
    // First do a round for specific event codes
    List<SubProcess> eventTriggeredSubProcesses =
        scope.getFlowElements().getEventTriggeredSubProcesses();
    for (SubProcess eventSsubProcess : eventTriggeredSubProcesses) {
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
            StartFlowNodeInstanceInfo startFlowNodeInstanceInfo =
                new StartFlowNodeInstanceInfo(eventSubProcessInstance, null);
            scope
                .getDirectInstanceResult()
                .addNewFlowNodeInstance(
                    processInstanceProcessingContext.getProcessInstance(),
                    startFlowNodeInstanceInfo);

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
              StartFlowNodeInstanceInfo startFlowNodeInstanceInfo =
                  new StartFlowNodeInstanceInfo(eventSubProcessInstance, null);
              scope
                  .getDirectInstanceResult()
                  .addNewFlowNodeInstance(
                      processInstanceProcessingContext.getProcessInstance(),
                      startFlowNodeInstanceInfo);
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

  private boolean processEventsByBoundaryEvents(
      ProcessInstanceProcessingContext processInstanceProcessingContext,
      Scope scope,
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

      boolean eventHandled =
          boundaryEventProcessor.processEvent(
              processInstanceProcessingContext, scope, boundaryEventInstance, event);
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
        boolean eventHandled =
            boundaryEventProcessor.processEventCatchAll(
                processInstanceProcessingContext, scope, boundaryEventInstance, event);
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
            boundaryEventProcessor.processAbort(
                processInstanceProcessingContext, scope, boundaryEventInstanceThatHandledEvent);
          }
        }
      }
    }
    return boundaryEventThatHandledEvent != null;
  }

  private void startSubscriptionsForEventSubprocesses(
      ProcessInstanceProcessingContext processInstanceProcessingContext, Scope scope) {
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

          String correlationKey =
              feelExpressionHandler
                  .processFeelExpression(
                      messageEventDefinition.getReferencedMessage().correlationKey(),
                      scope.getVariableScope())
                  .asText();

          String messageName = messageEventDefinition.getReferencedMessage().name();
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
}
