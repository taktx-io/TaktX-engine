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
import io.taktx.engine.feel.FeelExpressionHandler;
import io.taktx.engine.pd.model.Activity;
import io.taktx.engine.pd.model.EventDefinition;
import io.taktx.engine.pd.model.EventSignal;
import io.taktx.engine.pd.model.FlowNode;
import io.taktx.engine.pd.model.MessageEventDefinition;
import io.taktx.engine.pd.model.StartEvent;
import io.taktx.engine.pd.model.SubProcess;
import io.taktx.engine.pd.model.TimerEventDefinition;
import io.taktx.engine.pd.model.WIthChildElements;
import io.taktx.engine.pi.model.ActivityInstance;
import io.taktx.engine.pi.model.BoundaryEventInstance;
import io.taktx.engine.pi.model.ContinueFlowNodeInstanceInfo;
import io.taktx.engine.pi.model.FlowNodeInstance;
import io.taktx.engine.pi.model.NewCorrelationSubscriptionMessageEventInfo;
import io.taktx.engine.pi.model.ScheduledStartInfo;
import io.taktx.engine.pi.model.Scope;
import io.taktx.engine.pi.model.StartFlowNodeInstanceInfo;
import io.taktx.engine.pi.model.TerminateCorrelationSubscriptionMessageEventInfo;
import io.taktx.engine.pi.model.WithScope;
import io.taktx.engine.pi.processor.BoundaryEventInstanceProcessor;
import io.taktx.engine.pi.processor.FlowNodeInstanceProcessor;
import io.taktx.engine.pi.processor.FlowNodeInstanceProcessorProvider;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@ApplicationScoped
public class ScopeProcessor {

  private final FlowNodeInstanceProcessorProvider flowNodeInstanceProcessorProvider;
  private final FeelExpressionHandler feelExpressionHandler;
  private final BoundaryEventInstanceProcessor boundaryEventProcessor;

  private final Map<Long, Set<Long>> activityToBoundaryEvents = new java.util.WeakHashMap<>();
  private final Map<Long, Long> boundaryEventToActivity = new java.util.WeakHashMap<>();

  public ScopeProcessor(
      FlowNodeInstanceProcessorProvider flowNodeInstanceProcessorProvider,
      FeelExpressionHandler feelExpressionHandler,
      BoundaryEventInstanceProcessor boundaryEventProcessor) {
    this.flowNodeInstanceProcessorProvider = flowNodeInstanceProcessorProvider;
    this.feelExpressionHandler = feelExpressionHandler;
    this.boundaryEventProcessor = boundaryEventProcessor;
  }

  public void processStart(
      List<Long> parentElementInstanceIdPath,
      String elementId,
      ProcessInstanceProcessingContext processInstanceProcessingContext,
      Scope scope) {

    int subProcessLevel = scope.getSubProcessLevel();

    if (subProcessLevel < parentElementInstanceIdPath.size()) {
      FlowNodeInstance<?> instanceWithInstanceId =
          scope
              .getFlowNodeInstanceScope()
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
        FlowNode flowNode = withScope.getFlowNode();
        if (!(flowNode instanceof WIthChildElements)) {
          throw new IllegalArgumentException(
              "Element with instance id "
                  + parentElementInstanceIdPath.get(subProcessLevel)
                  + " is not a scope");
        }
        Scope childScope = scope.selectChildScope(withScope);
        processStart(
            parentElementInstanceIdPath, elementId, processInstanceProcessingContext, childScope);
      } else {
        throw new IllegalArgumentException(
            "Element with instance id "
                + parentElementInstanceIdPath.get(subProcessLevel)
                + " is not a scope");
      }
    } else if (subProcessLevel == parentElementInstanceIdPath.size()) {
      startSubscriptionsForEventSubprocesses(processInstanceProcessingContext, scope);

      createNewInstanceAndAddToDirectInstanceResult(
          processInstanceProcessingContext, scope, elementId);

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
              .getFlowNodeInstanceScope()
              .getInstanceWithInstanceId(elementInstanceIdPath.get(subProcessLevel));
      if (instanceWithInstanceId instanceof WithScope withScope) {
        FlowNode flowNode = withScope.getFlowNode();
        if (!(flowNode instanceof WIthChildElements wIthChildElements)) {
          throw new IllegalArgumentException(
              "Element with instance id "
                  + elementInstanceIdPath.get(subProcessLevel)
                  + " is not a scope");
        }

        Scope childScope = scope.selectChildScope(withScope);

        processContinue(
            processInstanceProcessingContext, childScope, trigger, elementInstanceIdPath);

        doBusiness(processInstanceProcessingContext, childScope);
      } else {
        throw new IllegalArgumentException(
            "Element with instance id "
                + elementInstanceIdPath.get(subProcessLevel)
                + " is not a scope");
      }
    } else {
      FlowNodeInstance<?> flowNodeInstance =
          scope
              .getFlowNodeInstanceScope()
              .getInstanceWithInstanceId(
                  trigger.getElementInstanceIdPath().get(scope.getSubProcessLevel()));

      ContinueFlowNodeInstanceInfo continueInstance =
          new ContinueFlowNodeInstanceInfo(flowNodeInstance, trigger);

      scope.getDirectInstanceResult().addContinueInstance(continueInstance);

      doBusiness(processInstanceProcessingContext, scope);
    }
  }

  public void processTerminate(
      ProcessInstanceProcessingContext processInstanceProcessingContext,
      Scope scope,
      AbortTriggerDTO trigger) {}

  private void doBusiness(
      ProcessInstanceProcessingContext processInstanceProcessingContext, Scope scope) {

    DirectInstanceResult directInstanceResult = scope.getDirectInstanceResult();
    while (directInstanceResult.hasDirectTriggers()) {
      processNewInstances(processInstanceProcessingContext, scope);
      processContinueInstances(processInstanceProcessingContext, scope);
      processEvents(processInstanceProcessingContext, scope);
    }
    scope.updateActiveCountForInstances();

    terminateEventSubprocessSubscriptionsIfDone(processInstanceProcessingContext, scope);
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

      startAttachedBoundaryEvents(processInstanceProcessingContext, scope, fLowNodeInstance);

      FlowNodeInstanceProcessor<?, ?, ?> processor =
          flowNodeInstanceProcessorProvider.getProcessor(fLowNodeInstance.getFlowNode());
      processor.processStart(
          processInstanceProcessingContext,
          scope,
          fLowNodeInstance,
          instanceInfo.inputSequenceFlowId());
      instanceInfo = scope.getDirectInstanceResult().pollNewFlowNodeInstance();
    }
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
                BoundaryEventInstance boundaryEventInstance =
                    new BoundaryEventInstance(
                        fLowNodeInstance.getParentInstance(),
                        boundaryEvent,
                        scope.nextElementInstanceId());

                activityToBoundaryEvents
                    .computeIfAbsent(
                        activityInstance.getElementInstanceId(), k -> new java.util.HashSet<>())
                    .add(boundaryEventInstance.getElementInstanceId());
                boundaryEventToActivity.put(
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

  private void processContinueInstances(
      ProcessInstanceProcessingContext processInstanceProcessingContext, Scope scope) {

    ContinueFlowNodeInstanceInfo continueInstance =
        scope.getDirectInstanceResult().pollContinueInstance();
    while (continueInstance != null) {
      FlowNodeInstance<?> flowNodeInstance = continueInstance.flowNodeInstance();

      if (flowNodeInstance instanceof BoundaryEventInstance boundaryEventInstance) {
        if (boundaryEventInstance.getFlowNode().isCancelActivity()) {
          Long activityInstanceId =
              boundaryEventToActivity.get(boundaryEventInstance.getElementInstanceId());
          if (activityInstanceId != null) {
            scope.getDirectInstanceResult().addCancelInstance(activityInstanceId);
          }
        }
      }

      FlowNodeInstanceProcessor<?, ?, ?> processor =
          flowNodeInstanceProcessorProvider.getProcessor(flowNodeInstance.getFlowNode());

      processor.processContinue(
          processInstanceProcessingContext, scope, flowNodeInstance, continueInstance.trigger());
      continueInstance = scope.getDirectInstanceResult().pollContinueInstance();
    }
  }

  private static void createNewInstanceAndAddToDirectInstanceResult(
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
  }

  private void processEventByFlowNodeInstance(
      ProcessInstanceProcessingContext processInstanceProcessingContext,
      Scope scope,
      EventSignal event,
      FlowNodeInstance<?> fLowNodeInstance) {
    boolean eventHandled = false;

    long elementInstanceId = event.getCurrentInstance().getElementInstanceId();
    Set<Long> boundaryEventIdsForInstance =
        activityToBoundaryEvents.getOrDefault(elementInstanceId, java.util.Collections.emptySet());
    DirectInstanceResult directInstanceResult = scope.getDirectInstanceResult();
    for (Long boundaryEventId : boundaryEventIdsForInstance) {
      BoundaryEventInstance boundaryEventInstance =
          (BoundaryEventInstance)
              scope.getFlowNodeInstanceScope().getInstanceWithInstanceId(boundaryEventId);

      eventHandled =
          boundaryEventProcessor.processEvent(
              processInstanceProcessingContext, scope, boundaryEventInstance, event);
      if (eventHandled) {
        if (boundaryEventInstance.getFlowNode().isCancelActivity()) {
          directInstanceResult.addCancelInstance(elementInstanceId);
        }
        break;
      }
    }
    if (!eventHandled) {
      // If not handled by specific codes, check for catch all
      for (Long boundaryEventId : boundaryEventIdsForInstance) {
        BoundaryEventInstance boundaryEventInstance =
            (BoundaryEventInstance)
                scope.getFlowNodeInstanceScope().getInstanceWithInstanceId(boundaryEventId);
        eventHandled =
            boundaryEventProcessor.processEventCatchAll(
                processInstanceProcessingContext, scope, boundaryEventInstance, event);
        if (eventHandled) {
          if (boundaryEventInstance.getFlowNode().isCancelActivity()) {
            directInstanceResult.addCancelInstance(elementInstanceId);
          }
          break;
        }
      }
    }

    // Still not handled
    if (!eventHandled) {
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
              // Create a new instanceToContinue for the event subprocess
              FlowNodeInstance<?> eventSubProcessInstance =
                  eventSsubProcess.createAndStoreNewInstance(
                      fLowNodeInstance.getParentInstance(), scope);
              StartFlowNodeInstanceInfo startFlowNodeInstanceInfo =
                  new StartFlowNodeInstanceInfo(eventSubProcessInstance, null);
              directInstanceResult.addNewFlowNodeInstance(
                  processInstanceProcessingContext.getProcessInstance(), startFlowNodeInstanceInfo);
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
                        fLowNodeInstance.getParentInstance(), scope);
                StartFlowNodeInstanceInfo startFlowNodeInstanceInfo =
                    new StartFlowNodeInstanceInfo(eventSubProcessInstance, null);
                directInstanceResult.addNewFlowNodeInstance(
                    processInstanceProcessingContext.getProcessInstance(),
                    startFlowNodeInstanceInfo);
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
        directInstanceResult.addAbortInstance(event.getCurrentInstance().getElementInstanceId());
      }
    }
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
