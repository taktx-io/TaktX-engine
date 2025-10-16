/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks All rights reserved.
 * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 * Free use is permitted with up to 3 Kafka partitions per topic. See LICENSE file for details.
 * For commercial use or more partitions and features, contact [https://www.taktx.io/contact].
 */

package io.taktx.engine.pi.processor;

import com.fasterxml.jackson.databind.JsonNode;
import io.taktx.dto.ContinueFlowElementTriggerDTO;
import io.taktx.dto.ExecutionState;
import io.taktx.engine.feel.FeelExpressionHandler;
import io.taktx.engine.pd.model.CatchEvent;
import io.taktx.engine.pd.model.EventSignal;
import io.taktx.engine.pd.model.Message;
import io.taktx.engine.pd.model.SignalEventDefinition;
import io.taktx.engine.pi.InstanceResult;
import io.taktx.engine.pi.ProcessInstanceMapper;
import io.taktx.engine.pi.ProcessInstanceProcessingContext;
import io.taktx.engine.pi.model.CancelInstanceSignalSubscriptionInfo;
import io.taktx.engine.pi.model.CatchEventInstance;
import io.taktx.engine.pi.model.NewCorrelationSubscriptionMessageEventInfo;
import io.taktx.engine.pi.model.NewInstanceSignalSubscriptionInfo;
import io.taktx.engine.pi.model.ProcessInstance;
import io.taktx.engine.pi.model.ScheduledContinuationInfo;
import io.taktx.engine.pi.model.Scope;
import io.taktx.engine.pi.model.TerminateCorrelationSubscriptionMessageEventInfo;
import java.time.Clock;
import java.util.Optional;
import lombok.NoArgsConstructor;

@NoArgsConstructor
public abstract class CatchEventInstanceProcessor<
        E extends CatchEvent, I extends CatchEventInstance<? extends CatchEvent>>
    extends EventInstanceProcessor<E, I> {

  private FeelExpressionHandler feelExpressionHandler;

  protected CatchEventInstanceProcessor(
      IoMappingProcessor ioMappingProcessor,
      ProcessInstanceMapper processInstanceMapper,
      FeelExpressionHandler feelExpressionHandler,
      Clock clock) {
    super(ioMappingProcessor, processInstanceMapper, clock);
    this.feelExpressionHandler = feelExpressionHandler;
  }

  @Override
  protected void processStartSpecificEventInstance(
      ProcessInstanceProcessingContext processInstanceProcessingContext,
      Scope scope,
      I catchEventInstance,
      String inputFlowId) {

    catchEventInstance.setState(ExecutionState.COMPLETED);

    catchEventInstance
        .getFlowNode()
        .getSignalEventDefinition()
        .ifPresent(
            signalEventDefinition -> {
              catchEventInstance.setState(ExecutionState.ACTIVE);
              String name =
                  feelExpressionHandler
                      .processFeelExpression(
                          signalEventDefinition.getReferencedSignal().name(),
                          scope.getVariableScope())
                      .asText();
              NewInstanceSignalSubscriptionInfo subscriptionInfo =
                  new NewInstanceSignalSubscriptionInfo(name, catchEventInstance);
              processInstanceProcessingContext
                  .getInstanceResult()
                  .addNewInstanceSignalSubscription(subscriptionInfo);
            });

    catchEventInstance
        .getFlowNode()
        .getEscalationEventDefinition()
        .ifPresent(
            escalationEventDefinition -> {
              catchEventInstance.setState(ExecutionState.ACTIVE);
              catchEventInstance.addEscalationSubscription(escalationEventDefinition);
            });

    catchEventInstance
        .getFlowNode()
        .getErrorEventDefinition()
        .ifPresent(
            errorEventDefinition -> {
              catchEventInstance.setState(ExecutionState.ACTIVE);
              catchEventInstance.addErrorSubscription(errorEventDefinition);
            });

    if (shoudHandleTimerEvents()) {
      catchEventInstance
          .getFlowNode()
          .getTimerEventDefinition()
          .ifPresent(
              timerEventDefinition -> {
                catchEventInstance.setState(ExecutionState.ACTIVE);
                processInstanceProcessingContext
                    .getInstanceResult()
                    .addNewScheduledContinuation(
                        new ScheduledContinuationInfo(
                            catchEventInstance, timerEventDefinition, scope.getVariableScope()));
              });
    }

    catchEventInstance
        .getFlowNode()
        .getMessageventDefinition()
        .ifPresent(
            messageEventDefinition -> {
              catchEventInstance.setState(ExecutionState.ACTIVE);
              Message message = messageEventDefinition.getReferencedMessage();
              String correlationKeyExpression = message.correlationKey();
              JsonNode jsonNode =
                  feelExpressionHandler.processFeelExpression(
                      correlationKeyExpression, scope.getVariableScope());
              String correlationKey = jsonNode.asText();
              String messageName = message.name();
              NewCorrelationSubscriptionMessageEventInfo messageInfo =
                  new NewCorrelationSubscriptionMessageEventInfo(
                      messageName, correlationKey, catchEventInstance, null);
              processInstanceProcessingContext
                  .getInstanceResult()
                  .addNewCorrelationSubcriptionMessageEvent(messageInfo);
            });
  }

  protected abstract boolean shoudHandleTimerEvents();

  @Override
  protected void processContinueSpecificFlowNodeInstance(
      ProcessInstanceProcessingContext processInstanceProcessingContext,
      Scope scope,
      I flowNodeInstance,
      ContinueFlowElementTriggerDTO trigger) {
    getInstanceResultForContinue(processInstanceProcessingContext, scope, flowNodeInstance);
  }

  private void getInstanceResultForContinue(
      ProcessInstanceProcessingContext processInstanceProcessingContext,
      Scope scope,
      I flowNodeInstance) {
    if (shouldCancel(flowNodeInstance)) {
      // Complete the catch event instance
      flowNodeInstance.setState(ExecutionState.COMPLETED);
      terminateSubscriptions(
          flowNodeInstance, processInstanceProcessingContext.getInstanceResult(), scope);
    }
    processContinueSpecificCatchEventInstance(
        processInstanceProcessingContext, scope, flowNodeInstance);
  }

  private void terminateSubscriptions(I flowNodeInstance, InstanceResult result, Scope scope) {
    terminateScheduleKeys(flowNodeInstance, result);
    terminateMessageSubscriptions(flowNodeInstance, result);
    terminateEscalationAndErrorSubscriptions(flowNodeInstance);
    terminateSignelSubscription(flowNodeInstance, result, scope);
  }

  private void terminateSignelSubscription(I flowNodeInstance, InstanceResult result, Scope scope) {
    Optional<SignalEventDefinition> signalEventDefinition =
        flowNodeInstance.getFlowNode().getSignalEventDefinition();
    if (signalEventDefinition.isPresent()) {
      String name =
          feelExpressionHandler
              .processFeelExpression(
                  signalEventDefinition.get().getReferencedSignal().name(),
                  scope.getVariableScope())
              .asText();

      result.addCancelInstanceSignalSubscription(
          new CancelInstanceSignalSubscriptionInfo(name, flowNodeInstance));
    }
  }

  private void terminateEscalationAndErrorSubscriptions(I flowNodeInstance) {
    flowNodeInstance.clearEscalationSubscriptions();
    flowNodeInstance.clearErrorSubscriptions();
  }

  private static <I extends CatchEventInstance<? extends CatchEvent>>
      void terminateMessageSubscriptions(I flowNodeInstance, InstanceResult result) {
    flowNodeInstance
        .getMessageEventKeys()
        .forEach(
            (messageEventKey, correlationKeys) ->
                correlationKeys.forEach(
                    correlationKey -> {
                      TerminateCorrelationSubscriptionMessageEventInfo messageEventInfo =
                          new TerminateCorrelationSubscriptionMessageEventInfo(
                              messageEventKey.getMessageName(), correlationKey);
                      result.addTerminateCorrelationSubscriptionMessageEvent(messageEventInfo);
                    }));
  }

  private static <I extends CatchEventInstance<? extends CatchEvent>> void terminateScheduleKeys(
      I flowNodeInstance, InstanceResult result) {
    flowNodeInstance.getScheduledKeys().forEach(result::cancelSchedule);
  }

  protected abstract void processContinueSpecificCatchEventInstance(
      ProcessInstanceProcessingContext processInstanceProcessingContext,
      Scope scope,
      I flowNodeInstance);

  protected abstract boolean shouldCancel(I flowNodeInstance);

  @Override
  protected void processAbortSpecificFlowNodeInstance(
      ProcessInstanceProcessingContext processInstanceProcessingContext, Scope scope, I instance) {
    terminateSubscriptions(instance, processInstanceProcessingContext.getInstanceResult(), scope);
  }

  public boolean processEvent(
      ProcessInstanceProcessingContext processInstanceProcessingContext,
      Scope scope,
      I catchEventInstance,
      EventSignal event) {
    long now = clock.millis();

    InstanceResult newInstanceResult = processInstanceProcessingContext.getInstanceResult();
    if (catchEventInstance.matchesEvent(event)) {
      getInstanceResultForContinue(processInstanceProcessingContext, scope, catchEventInstance);
      ProcessInstance processInstance = processInstanceProcessingContext.getProcessInstance();
      selectNextNodeIfAllowedContinue(catchEventInstance, processInstance, scope);
      newInstanceResult.addInstanceUpdate(
          createFlowNodeInstanceUpdate(processInstance, catchEventInstance, scope, now));
      return true;
    }
    return false;
  }

  public boolean processEventCatchAll(
      ProcessInstanceProcessingContext processInstanceProcessingContext,
      Scope scope,
      I catchEventInstance,
      EventSignal event) {
    long now = clock.millis();

    if (catchEventInstance.matchesEventCatchAll(event)) {
      InstanceResult instanceResult = processInstanceProcessingContext.getInstanceResult();
      getInstanceResultForContinue(processInstanceProcessingContext, scope, catchEventInstance);
      ProcessInstance processInstance = processInstanceProcessingContext.getProcessInstance();
      selectNextNodeIfAllowedContinue(catchEventInstance, processInstance, scope);
      instanceResult.addInstanceUpdate(
          createFlowNodeInstanceUpdate(processInstance, catchEventInstance, scope, now));
      return true;
    }
    return false;
  }
}
