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
import io.taktx.engine.pd.model.SignalEvent;
import io.taktx.engine.pd.model.SignalEventDefinition;
import io.taktx.engine.pi.InstanceResult;
import io.taktx.engine.pi.ProcessInstanceException;
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
import io.taktx.engine.pi.model.VariableScope;
import java.time.Clock;
import java.util.List;
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
      VariableScope variableScope,
      I catchEventInstance,
      String inputFlowId) {

    catchEventInstance.setState(ExecutionState.COMPLETED);

    catchEventInstance
        .getFlowNode()
        .getSignalEventDefinition()
        .ifPresent(
            signalEventDefinition -> {
              catchEventInstance.setState(ExecutionState.ACTIVE);

              if (signalEventDefinition.getReferencedSignal() == null) {
                throw new ProcessInstanceException(
                    catchEventInstance, "SignalEventDefinition has no referenced signal");
              }

              JsonNode jsonNode =
                  feelExpressionHandler.processFeelExpression(
                      signalEventDefinition.getReferencedSignal().name(), variableScope);

              if (jsonNode == null || jsonNode.isNull()) {
                throw new ProcessInstanceException(
                    catchEventInstance, "Signal name expression returned null");
              }

              String name = jsonNode.asText();
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
                            catchEventInstance, timerEventDefinition, variableScope));
              });
    }

    catchEventInstance
        .getFlowNode()
        .getMessageventDefinition()
        .ifPresent(
            messageEventDefinition -> {
              catchEventInstance.setState(ExecutionState.ACTIVE);
              Message message = messageEventDefinition.getReferencedMessage();
              if (message == null) {
                throw new ProcessInstanceException(
                    catchEventInstance, "Message event definition has no referenced message");
              }

              String correlationKeyExpression = message.correlationKey();
              JsonNode jsonNode =
                  feelExpressionHandler.processFeelExpression(
                      correlationKeyExpression, variableScope);
              if (jsonNode == null || jsonNode.isNull()) {
                throw new ProcessInstanceException(
                    catchEventInstance, "Correlation key expression returned null");
              }
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
      VariableScope variableScope,
      I flowNodeInstance,
      ContinueFlowElementTriggerDTO trigger) {
    getInstanceResultForContinue(
        processInstanceProcessingContext, scope, variableScope, flowNodeInstance);
  }

  private void getInstanceResultForContinue(
      ProcessInstanceProcessingContext processInstanceProcessingContext,
      Scope scope,
      VariableScope variableScope,
      I flowNodeInstance) {
    if (shouldCancel(flowNodeInstance)) {
      // Complete the catch event instance
      flowNodeInstance.setState(ExecutionState.COMPLETED);
      terminateSubscriptions(
          flowNodeInstance, processInstanceProcessingContext.getInstanceResult(), variableScope);
    }
    processContinueSpecificCatchEventInstance(
        processInstanceProcessingContext, scope, flowNodeInstance);
  }

  private void terminateSubscriptions(
      I flowNodeInstance, InstanceResult result, VariableScope variableScope) {
    terminateScheduleKeys(flowNodeInstance, result);
    terminateMessageSubscriptions(flowNodeInstance, result);
    terminateEscalationAndErrorSubscriptions(flowNodeInstance);
    terminateSignalSubscription(flowNodeInstance, result, variableScope);
  }

  private void terminateSignalSubscription(
      I flowNodeInstance, InstanceResult result, VariableScope variableScope) {
    Optional<SignalEventDefinition> signalEventDefinition =
        flowNodeInstance.getFlowNode().getSignalEventDefinition();
    if (signalEventDefinition.isPresent()) {
      SignalEvent referencedSignal = signalEventDefinition.get().getReferencedSignal();
      if (referencedSignal == null) {
        throw new ProcessInstanceException(
            flowNodeInstance, "SignalEventDefinition has no referenced signal");
      }
      JsonNode jsonNode =
          feelExpressionHandler.processFeelExpression(referencedSignal.name(), variableScope);
      if (jsonNode == null || jsonNode.isNull()) {
        throw new ProcessInstanceException(
            flowNodeInstance, "Signal name expression returned null");
      }
      String name = jsonNode.asText();

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
      ProcessInstanceProcessingContext processInstanceProcessingContext,
      Scope scope,
      VariableScope variableScope,
      I instance) {
    terminateSubscriptions(
        instance, processInstanceProcessingContext.getInstanceResult(), variableScope);
  }

  public boolean processEvent(
      ProcessInstanceProcessingContext processInstanceProcessingContext,
      Scope scope,
      VariableScope variableScope,
      I catchEventInstance,
      EventSignal event) {
    long now = clock.millis();

    ioMappingProcessor.processInputMappings(catchEventInstance.getFlowNode(), variableScope);

    InstanceResult newInstanceResult = processInstanceProcessingContext.getInstanceResult();
    if (catchEventInstance.matchesEvent(event)) {
      variableScope.merge(event.getVariables());
      getInstanceResultForContinue(
          processInstanceProcessingContext, scope, variableScope, catchEventInstance);
      ioMappingProcessor.processOutputMappings(catchEventInstance.getFlowNode(), variableScope);
      ProcessInstance processInstance = processInstanceProcessingContext.getProcessInstance();
      selectNextNodeIfAllowedContinue(catchEventInstance, processInstance, scope, variableScope);
      List<String> outputSequenceFlowIds =
          scope.getDirectInstanceResult().getSequenceFlowsFromNewFlowNodeInstances();
      newInstanceResult.addInstanceUpdate(
          createFlowNodeInstanceUpdate(
              processInstance,
              catchEventInstance,
              scope,
              variableScope,
              now,
              null,
              outputSequenceFlowIds));
      return true;
    }
    return false;
  }

  public boolean processEventCatchAll(
      ProcessInstanceProcessingContext processInstanceProcessingContext,
      Scope scope,
      VariableScope variableScope,
      I catchEventInstance,
      EventSignal event) {
    long now = clock.millis();

    ioMappingProcessor.processInputMappings(catchEventInstance.getFlowNode(), variableScope);

    if (catchEventInstance.matchesEventCatchAll(event)) {
      variableScope.merge(event.getVariables());
      InstanceResult instanceResult = processInstanceProcessingContext.getInstanceResult();
      getInstanceResultForContinue(
          processInstanceProcessingContext, scope, variableScope, catchEventInstance);
      ioMappingProcessor.processOutputMappings(catchEventInstance.getFlowNode(), variableScope);

      ProcessInstance processInstance = processInstanceProcessingContext.getProcessInstance();
      selectNextNodeIfAllowedContinue(catchEventInstance, processInstance, scope, variableScope);
      List<String> sequenceFlowIds =
          scope.getDirectInstanceResult().getSequenceFlowsFromNewFlowNodeInstances();

      instanceResult.addInstanceUpdate(
          createFlowNodeInstanceUpdate(
              processInstance,
              catchEventInstance,
              scope,
              variableScope,
              now,
              null,
              sequenceFlowIds));
      return true;
    }
    return false;
  }
}
