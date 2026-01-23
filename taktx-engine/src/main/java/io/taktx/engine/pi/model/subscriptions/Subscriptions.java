package io.taktx.engine.pi.model.subscriptions;

import io.taktx.dto.ContinueFlowElementTriggerDTO;
import io.taktx.dto.subscriptions.SubScriptionType;
import io.taktx.engine.feel.FeelExpressionHandler;
import io.taktx.engine.pd.model.BoundaryEvent;
import io.taktx.engine.pd.model.ErrorEventDefinition;
import io.taktx.engine.pd.model.EscalationEventDefinition;
import io.taktx.engine.pd.model.EventDefinition;
import io.taktx.engine.pd.model.EventSignal;
import io.taktx.engine.pd.model.FlowNode;
import io.taktx.engine.pd.model.Message;
import io.taktx.engine.pd.model.MessageEventDefinition;
import io.taktx.engine.pd.model.ReceiveTask;
import io.taktx.engine.pd.model.StartEvent;
import io.taktx.engine.pd.model.SubProcess;
import io.taktx.engine.pd.model.TimerEventDefinition;
import io.taktx.engine.pi.ProcessInstanceProcessingContext;
import io.taktx.engine.pi.model.BoundaryEventInstance;
import io.taktx.engine.pi.model.ContinueFlowNodeInstanceInfo;
import io.taktx.engine.pi.model.FlowNodeInstance;
import io.taktx.engine.pi.model.IFlowNodeInstance;
import io.taktx.engine.pi.model.IntermediateCatchEventInstance;
import io.taktx.engine.pi.model.ReceiveTaskInstance;
import io.taktx.engine.pi.model.Scope;
import io.taktx.engine.pi.model.StartFlowNodeInstanceInfo;
import io.taktx.engine.pi.model.VariableScope;
import io.taktx.engine.pi.model.WithScope;
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
      ProcessInstanceProcessingContext context,
      VariableScope variableScope,
      BoundaryEvent boundaryEvent,
      FlowNodeInstance<?> parentInstance,
      FeelExpressionHandler feelExpressionHandler) {
    for (EventDefinition eventDefinition : boundaryEvent.getEventDefinitions()) {
      addSubscriptionForBoundaryEventDefinition(
          context,
          variableScope,
          boundaryEvent,
          eventDefinition,
          parentInstance,
          feelExpressionHandler);
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
              processInstanceProcessingContext,
              variableScope,
              feelExpressionHandler,
              eventTriggeredSubProcess,
              eventDefinition,
              scope.getParentFlowNodeInstance());
        }
      }
    }
  }

  public void terminateEventSubprocessSubscriptionsIfDone(
      ProcessInstanceProcessingContext processInstanceProcessingContext, Scope scope) {

    if (scope.getState().isDone()) {
      instanceSubscriptions.forEach(
          (fni, subscriptions) ->
              subscriptions.forEach(
                  subscription ->
                      subscription.cancel(processInstanceProcessingContext, scope, null)));
    }
  }

  private void addSubscriptionForEventSubProcessDefinition(
      ProcessInstanceProcessingContext processInstanceProcessingContext,
      VariableScope variableScope,
      FeelExpressionHandler feelExpressionHandler,
      SubProcess subProcess,
      EventDefinition eventDefinition,
      WithScope parentInstance) {
    List<Subscription> subscriptionList =
        instanceSubscriptions.computeIfAbsent(-1L, k -> new ArrayList<>());
    if (eventDefinition instanceof ErrorEventDefinition errorEventDefinition) {
      if (errorEventDefinition.getReferencedError() == null) {
        subscriptionList.add(new CatchAllErrorSubscription(subProcess.getId()));
      } else {
        subscriptionList.add(
            new ErrorSubscription(
                subProcess.getId(), errorEventDefinition.getReferencedError().code()));
      }
    } else if (eventDefinition instanceof EscalationEventDefinition escalationEventDefinition) {
      if (escalationEventDefinition.getReferencedEscalation() == null) {
        subscriptionList.add(new CatchAllEscalationSubscription(subProcess.getId()));
      } else {
        subscriptionList.add(
            new EscalationSubscription(
                subProcess.getId(), escalationEventDefinition.getReferencedEscalation().code()));
      }
    } else if (eventDefinition instanceof MessageEventDefinition messageEventDefinition) {
      subscriptionList.add(
          MessageSubscription.starting(
              processInstanceProcessingContext,
              variableScope,
              parentInstance,
              subProcess,
              messageEventDefinition.getReferencedMessage(),
              feelExpressionHandler));
    } else if (eventDefinition instanceof TimerEventDefinition timerEventDefinition) {
      subscriptionList.add(
          TimerSubscription.starting(
              processInstanceProcessingContext,
              parentInstance,
              subProcess,
              timerEventDefinition,
              variableScope));
    }
  }

  private void addSubscriptionForBoundaryEventDefinition(
      ProcessInstanceProcessingContext processInstanceProcessingContext,
      VariableScope variableScope,
      BoundaryEvent boundaryEvent,
      EventDefinition eventDefinition,
      FlowNodeInstance<?> parentInstance,
      FeelExpressionHandler feelExpressionHandler) {
    assert parentInstance != null;

    long elementInstanceId = parentInstance.getElementInstanceId();
    List<Subscription> subscriptionList =
        instanceSubscriptions.computeIfAbsent(elementInstanceId, k -> new ArrayList<>());
    if (eventDefinition instanceof ErrorEventDefinition errorEventDefinition) {
      if (errorEventDefinition.getReferencedError() == null) {
        subscriptionList.add(new CatchAllErrorSubscription(boundaryEvent.getId()));
      } else {
        subscriptionList.add(
            new ErrorSubscription(
                boundaryEvent.getId(), errorEventDefinition.getReferencedError().code()));
      }
    } else if (eventDefinition instanceof EscalationEventDefinition escalationEventDefinition) {
      if (escalationEventDefinition.getReferencedEscalation() == null) {
        subscriptionList.add(new CatchAllEscalationSubscription(boundaryEvent.getId()));
      } else {
        subscriptionList.add(
            new EscalationSubscription(
                boundaryEvent.getId(), escalationEventDefinition.getReferencedEscalation().code()));
      }
    } else if (eventDefinition instanceof MessageEventDefinition messageEventDefinition) {
      subscriptionList.add(
          MessageSubscription.starting(
              processInstanceProcessingContext,
              variableScope,
              parentInstance,
              boundaryEvent,
              messageEventDefinition.getReferencedMessage(),
              feelExpressionHandler));
    } else if (eventDefinition instanceof TimerEventDefinition timerEventDefinition) {
      subscriptionList.add(
          TimerSubscription.starting(
              processInstanceProcessingContext,
              parentInstance,
              boundaryEvent,
              timerEventDefinition,
              variableScope));
    }
  }

  public boolean processEvent(
      Scope scope,
      VariableScope variableScope,
      EventSignal event,
      IFlowNodeInstance elementInstance) {

    long elementInstanceId = elementInstance != null ? elementInstance.getElementInstanceId() : -1;
    Optional<Subscription> matchingSubscription =
        instanceSubscriptions.getOrDefault(elementInstanceId, List.of()).stream()
            .sorted(Comparator.comparing(ISubscription::order))
            .filter(subscription -> subscription.matchesEvent(event))
            .findFirst();

    if (matchingSubscription.isEmpty()) {
      matchingSubscription =
          instanceSubscriptions.getOrDefault(-1L, List.of()).stream()
              .sorted(Comparator.comparing(ISubscription::order))
              .filter(subscription -> subscription.matchesEvent(event))
              .findFirst();
    }

    if (matchingSubscription.isEmpty()) {
      return false;
    }

    Subscription subscription = matchingSubscription.get();
    if (subscription.getSubScriptionType() == SubScriptionType.STARTING) {
      FlowNode flowNode =
          scope.getFlowElements().getFlowNode(subscription.getElementId()).orElseThrow();
      FlowNodeInstance<?> flowNodeInstance =
          flowNode.createAndStoreNewInstance(scope.getParentFlowNodeInstance(), scope);
      if (flowNodeInstance instanceof BoundaryEventInstance boundaryEventInstance) {
        boundaryEventInstance.setAttachedInstanceId(elementInstanceId);
      }
      VariableScope childVariableScope = variableScope.selectChildScope(flowNodeInstance);
      StartFlowNodeInstanceInfo startInfo =
          new StartFlowNodeInstanceInfo(flowNodeInstance, null, childVariableScope);
      scope.getDirectInstanceResult().addNewFlowNodeInstance(startInfo);
    } else if (subscription.getSubScriptionType() == SubScriptionType.CONTINUING) {
      FlowNodeInstance<?> flowNodeInstance =
          scope.getFlowNodeInstances().getInstanceWithInstanceId(elementInstanceId);
      VariableScope childVariableScope = variableScope.selectChildScope(flowNodeInstance);
      ContinueFlowElementTriggerDTO trigger =
          new ContinueFlowElementTriggerDTO(
              scope.getProcessInstanceId(),
              flowNodeInstance.createKeyPath(),
              null,
              event.getVariables());
      ContinueFlowNodeInstanceInfo continueInfo =
          new ContinueFlowNodeInstanceInfo(flowNodeInstance, trigger, childVariableScope);
      scope.getDirectInstanceResult().addContinueInstance(continueInfo);
    }

    return true;
  }

  public void cancelSubscriptionsForInstance(
      ProcessInstanceProcessingContext processInstanceProcessingContext,
      FlowNodeInstance<?> instance,
      Scope scope) {
    long elementInstanceId = instance != null ? instance.getElementInstanceId() : -1;
    instanceSubscriptions
        .getOrDefault(elementInstanceId, List.of())
        .forEach(
            subscription -> subscription.cancel(processInstanceProcessingContext, scope, instance));
  }

  public void startSubscriptionsForIntermediateCatchEventInstance(
      ProcessInstanceProcessingContext processInstanceProcessingContext,
      VariableScope variableScope,
      FeelExpressionHandler feelExpressionHandler,
      IntermediateCatchEventInstance flowNodeInstance) {
    List<Subscription> subscriptionList =
        instanceSubscriptions.computeIfAbsent(
            flowNodeInstance.getElementInstanceId(), i -> new ArrayList<>());
    flowNodeInstance
        .getFlowNode()
        .getEventDefinitions()
        .forEach(
            eventDefinition -> {
              if (eventDefinition instanceof MessageEventDefinition messageEventDefinition) {
                subscriptionList.add(
                    MessageSubscription.continuing(
                        processInstanceProcessingContext,
                        variableScope,
                        flowNodeInstance,
                        messageEventDefinition.getReferencedMessage(),
                        feelExpressionHandler));
              } else if (eventDefinition instanceof TimerEventDefinition timerEventDefinition) {
                subscriptionList.add(
                    TimerSubscription.continuing(
                        processInstanceProcessingContext,
                        flowNodeInstance,
                        timerEventDefinition,
                        variableScope));
              }
            });
  }

  public void startSubscriptionsForReceiveTask(
      ProcessInstanceProcessingContext processInstanceProcessingContext,
      VariableScope variableScope,
      FeelExpressionHandler feelExpressionHandler,
      ReceiveTaskInstance receiveTaskInstance) {
    List<Subscription> subscriptionList =
        instanceSubscriptions.computeIfAbsent(
            receiveTaskInstance.getElementInstanceId(), i -> new ArrayList<>());

    ReceiveTask receiveTask = receiveTaskInstance.getFlowNode();
    Message referencedMessage = receiveTask.getReferencedMessage();
    subscriptionList.add(
        MessageSubscription.continuing(
            processInstanceProcessingContext,
            variableScope,
            receiveTaskInstance,
            referencedMessage,
            feelExpressionHandler));
  }
}
