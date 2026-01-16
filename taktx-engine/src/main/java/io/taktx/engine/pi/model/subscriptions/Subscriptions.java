package io.taktx.engine.pi.model.subscriptions;

import com.fasterxml.jackson.databind.JsonNode;
import io.taktx.engine.feel.FeelExpressionHandler;
import io.taktx.engine.pd.model.BoundaryEvent;
import io.taktx.engine.pd.model.ErrorEventDefinition;
import io.taktx.engine.pd.model.EscalationEventDefinition;
import io.taktx.engine.pd.model.EventDefinition;
import io.taktx.engine.pd.model.EventSignal;
import io.taktx.engine.pd.model.Message;
import io.taktx.engine.pd.model.MessageEventDefinition;
import io.taktx.engine.pd.model.StartEvent;
import io.taktx.engine.pd.model.SubProcess;
import io.taktx.engine.pd.model.TimerEventDefinition;
import io.taktx.engine.pi.ProcessInstanceProcessingContext;
import io.taktx.engine.pi.model.FlowNodeInstance;
import io.taktx.engine.pi.model.NewCorrelationSubscriptionMessageEventInfo;
import io.taktx.engine.pi.model.ScheduledStartInfo;
import io.taktx.engine.pi.model.Scope;
import io.taktx.engine.pi.model.TerminateCorrelationSubscriptionMessageEventInfo;
import io.taktx.engine.pi.model.VariableScope;
import io.taktx.engine.pi.model.WithScope;
import jakarta.ws.rs.ProcessingException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class Subscriptions {

  private Map<Long, List<Subscription>> instanceSubscriptions = new HashMap<>();

  public void addSubscriptionsForBoundaryEventDefinitions(
      BoundaryEvent boundaryEvent, FlowNodeInstance<?> parentInstance) {
    for (EventDefinition eventDefinition : boundaryEvent.getEventDefinitions()) {
      addSubscriptionForBoundaryEventDefinition(boundaryEvent, eventDefinition, parentInstance);
    }
  }

  public void startSubscriptionsForEventSubprocesses(
      ProcessInstanceProcessingContext processInstanceProcessingContext,
      Scope scope,
      FeelExpressionHandler feelExpressionHandler,
      VariableScope variableScope) {
    // first check if we need to start timer triggers for event subprocesses with corresponding
    // timer start events

    List<SubProcess> eventTriggeredSubProcesses =
        scope.getFlowElements().getEventTriggeredSubProcesses();
    for (SubProcess eventTriggeredSubProcess : eventTriggeredSubProcesses) {
      List<StartEvent> startEvents = eventTriggeredSubProcess.getElements().getStartEvents();
      for (StartEvent startEvent : startEvents) {

        for (EventDefinition eventDefinition : startEvent.getEventDefinitions()) {
          addSubscriptionForEventSubProcessDefinition(
              eventTriggeredSubProcess,
              startEvent,
              eventDefinition,
              scope.getParentFlowNodeInstance());
        }

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

  public void terminateEventSubprocessSubscriptionsIfDone(
      ProcessInstanceProcessingContext processInstanceProcessingContext, Scope scope) {

    if (scope.getState().isDone()) {
      instanceSubscriptions.forEach(
          (fni, subscriptions) -> {
            subscriptions.forEach(
                subscription -> {
                  if (subscription instanceof AbstractEventSubprocessSubscription) {
                    FlowNodeInstance<?> instanceWithInstanceId =
                        scope.getFlowNodeInstances().getInstanceWithInstanceId(fni);
                    subscription.cancel(scope, instanceWithInstanceId);
                  }
                });
          });

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

  private void addSubscriptionForEventSubProcessDefinition(
      SubProcess eventTriggeredSubProcess,
      StartEvent startEvent,
      EventDefinition eventDefinition,
      WithScope parentInstance) {
    List<Subscription> subscriptionList =
        instanceSubscriptions.computeIfAbsent(
            parentInstance.getElementInstanceId(), k -> new ArrayList<>());
    if (eventDefinition instanceof ErrorEventDefinition errorEventDefinition) {
      if (errorEventDefinition.getReferencedError() == null) {
        subscriptionList.add(
            new EventSubProcessCatchAllErrorSubscription(eventTriggeredSubProcess, startEvent));
      } else {
        subscriptionList.add(
            new EventSubProcessErrorSubscription(
                eventTriggeredSubProcess,
                startEvent,
                errorEventDefinition.getReferencedError().code()));
      }
    } else if (eventDefinition instanceof EscalationEventDefinition escalationEventDefinition) {
      if (escalationEventDefinition.getReferencedEscalation() == null) {
        subscriptionList.add(
            new EventSubProcessCatchAllEscalationSubscription(
                eventTriggeredSubProcess, startEvent));
      } else {
        subscriptionList.add(
            new EventSubProcessEscalationSubscription(
                eventTriggeredSubProcess,
                startEvent,
                escalationEventDefinition.getReferencedEscalation().code()));
      }
    }
  }

  private void addSubscriptionForBoundaryEventDefinition(
      BoundaryEvent boundaryEvent,
      EventDefinition eventDefinition,
      FlowNodeInstance<?> parentInstance) {
    List<Subscription> subscriptionList =
        instanceSubscriptions.computeIfAbsent(
            parentInstance.getElementInstanceId(), k -> new ArrayList<>());
    if (eventDefinition instanceof ErrorEventDefinition errorEventDefinition) {
      if (errorEventDefinition.getReferencedError() == null) {
        subscriptionList.add(new BoundaryEventCatchAllErrorSubscription(boundaryEvent));
      } else {
        subscriptionList.add(
            new BoundaryEventErrorSubscription(
                boundaryEvent, errorEventDefinition.getReferencedError().code()));
      }
    } else if (eventDefinition instanceof EscalationEventDefinition escalationEventDefinition) {
      if (escalationEventDefinition.getReferencedEscalation() == null) {
        subscriptionList.add(new BoundaryEventCatchAllEscalationSubscription(boundaryEvent));
      } else {
        subscriptionList.add(
            new BoundaryEventEscalationSubscription(
                boundaryEvent, escalationEventDefinition.getReferencedEscalation().code()));
      }
    }
  }

  public boolean processEvent(
      Scope scope,
      VariableScope variableScope,
      EventSignal event,
      FlowNodeInstance<?> elementInstance) {

    Optional<Subscription> matchingSubscription =
        instanceSubscriptions
            .getOrDefault(elementInstance.getElementInstanceId(), List.of())
            .stream()
            .sorted(Comparator.comparing(ISubscription::order))
            .filter(subscription -> subscription.matchesEvent(event))
            .findFirst();

    if (matchingSubscription.isEmpty()) {
      return false;
    }
    variableScope.merge(event.getVariables());
    matchingSubscription.get().process(scope, variableScope, event, elementInstance);
    return true;
  }

  public void cancelSubscriptionsForInstance(FlowNodeInstance<?> instance, Scope scope) {
    instanceSubscriptions
        .getOrDefault(instance.getElementInstanceId(), List.of())
        .forEach(subscription -> subscription.cancel(scope, instance));
  }
}
