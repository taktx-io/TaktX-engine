/*
 *
 *  * TaktX - A high-performance BPMN engine
 *  * Copyright (c) 2025 TaktX B.V. All rights reserved.
 *  * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 *  * Free use is permitted with up to 3 Kafka partitions. See LICENSE file for details.
 *  * For commercial use or more partitions and features, contact [info@taktx.io] or [https://www.taktx.io/contact].
 *
 */

package io.taktx.engine.pi.processor;

import com.fasterxml.jackson.databind.JsonNode;
import io.taktx.dto.CatchEventStateEnum;
import io.taktx.dto.ContinueFlowElementTriggerDTO;
import io.taktx.engine.feel.FeelExpressionHandler;
import io.taktx.engine.pd.model.CatchEvent;
import io.taktx.engine.pd.model.EventSignal;
import io.taktx.engine.pd.model.Message;
import io.taktx.engine.pi.DirectInstanceResult;
import io.taktx.engine.pi.FlowNodeInstanceProcessingContext;
import io.taktx.engine.pi.InstanceResult;
import io.taktx.engine.pi.ProcessInstanceMapper;
import io.taktx.engine.pi.ProcessInstanceProcessingContext;
import io.taktx.engine.pi.model.CatchEventInstance;
import io.taktx.engine.pi.model.NewCorrelationSubscriptionMessageEventInfo;
import io.taktx.engine.pi.model.ProcessInstance;
import io.taktx.engine.pi.model.ScheduledContinuationInfo;
import io.taktx.engine.pi.model.TerminateCorrelationSubscriptionMessageEventInfo;
import io.taktx.engine.pi.model.VariableScope;
import java.time.Clock;
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
      FlowNodeInstanceProcessingContext flowNodeInstanceProcessingContext,
      I catchEventInstance,
      String inputFlowId,
      VariableScope variables) {

    catchEventInstance.setState(CatchEventStateEnum.FINISHED);

    catchEventInstance
        .getFlowNode()
        .getEscalationEventDefinition()
        .ifPresent(
            escalationEventDefinition -> {
              catchEventInstance.setState(CatchEventStateEnum.WAITING);
              catchEventInstance.addEscalationSubscription(escalationEventDefinition);
            });

    catchEventInstance
        .getFlowNode()
        .getErrorEventDefinition()
        .ifPresent(
            errorEventDefinition -> {
              catchEventInstance.setState(CatchEventStateEnum.WAITING);
              catchEventInstance.addErrorSubscription(errorEventDefinition);
            });

    if (shoudHandleTimerxEvents()) {
      catchEventInstance
          .getFlowNode()
          .getTimerEventDefinition()
          .ifPresent(
              timerEventDefinition -> {
                catchEventInstance.setState(CatchEventStateEnum.WAITING);
                processInstanceProcessingContext
                    .getInstanceResult()
                    .addNewScheduledContinuation(
                        new ScheduledContinuationInfo(
                            catchEventInstance, timerEventDefinition, variables));
              });
    }
    catchEventInstance
        .getFlowNode()
        .getMessageventDefinition()
        .ifPresent(
            messageEventDefinition -> {
              catchEventInstance.setState(CatchEventStateEnum.WAITING);
              Message message = messageEventDefinition.getReferencedMessage();
              String correlationKeyExpression = message.correlationKey();
              JsonNode jsonNode =
                  feelExpressionHandler.processFeelExpression(correlationKeyExpression, variables);
              String correlationKey = jsonNode.asText();
              String messageName = message.name();
              NewCorrelationSubscriptionMessageEventInfo messageInfo =
                  new NewCorrelationSubscriptionMessageEventInfo(
                      messageName, correlationKey, catchEventInstance);
              processInstanceProcessingContext
                  .getInstanceResult()
                  .addNewCorrelationSubcriptionMessageEvent(messageInfo);
            });
  }

  protected abstract boolean shoudHandleTimerxEvents();

  @Override
  protected void processContinueSpecificFlowNodeInstance(
      ProcessInstanceProcessingContext processInstanceProcessingContext,
      FlowNodeInstanceProcessingContext flowNodeInstanceProcessingContext,
      int subProcessLevel,
      I flowNodeInstance,
      ContinueFlowElementTriggerDTO trigger,
      VariableScope variables) {
    getInstanceResultForContinue(
        processInstanceProcessingContext,
        flowNodeInstanceProcessingContext.getDirectInstanceResult(),
        flowNodeInstance);
  }

  private void getInstanceResultForContinue(
      ProcessInstanceProcessingContext processInstanceProcessingContext,
      DirectInstanceResult directInstanceResult,
      I flowNodeInstance) {

    if (shouldCancel(flowNodeInstance)) {
      flowNodeInstance.setState(CatchEventStateEnum.FINISHED);
      terminateSubscriptions(
          flowNodeInstance, processInstanceProcessingContext.getInstanceResult());
    }
    processContinueSpecificCatchEventInstance(
        processInstanceProcessingContext, directInstanceResult, flowNodeInstance);
  }

  private void terminateSubscriptions(I flowNodeInstance, InstanceResult result) {
    terminateScheduleKeys(flowNodeInstance, result);
    terminateMessageSubscriptions(flowNodeInstance, result);
    terminateEscalationAndErrorSubscriptions(flowNodeInstance);
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
      DirectInstanceResult directInstanceResult,
      I flowNodeInstance);

  protected abstract boolean shouldCancel(I flowNodeInstance);

  @Override
  protected void processTerminateSpecificFlowNodeInstance(
      ProcessInstanceProcessingContext processInstanceProcessingContext,
      FlowNodeInstanceProcessingContext flowNodeInstanceProcessingContext,
      I instance,
      VariableScope currentVariableScope) {
    terminateSubscriptions(instance, processInstanceProcessingContext.getInstanceResult());
  }

  public boolean processEvent(
      ProcessInstanceProcessingContext processInstanceProcessingContext,
      FlowNodeInstanceProcessingContext flowNodeInstanceProcessingContext,
      I catchEventInstance,
      EventSignal event,
      VariableScope parentVariableScope) {
    long now = clock.millis();
    VariableScope boundaryEventVariableScope =
        parentVariableScope.selectFlowNodeInstancesScope(catchEventInstance.getElementInstanceId());

    InstanceResult newInstanceResult = processInstanceProcessingContext.getInstanceResult();
    if (catchEventInstance.matchesEvent(event)) {
      getInstanceResultForContinue(
          processInstanceProcessingContext,
          flowNodeInstanceProcessingContext.getDirectInstanceResult(),
          catchEventInstance);
      ProcessInstance processInstance = processInstanceProcessingContext.getProcessInstance();
      selectNextNodeIfAllowedContinue(
          catchEventInstance,
          processInstance,
          boundaryEventVariableScope,
          flowNodeInstanceProcessingContext);
      newInstanceResult.addInstanceUpdate(
          createFlowNodeInstanceUpdate(
              processInstance,
              catchEventInstance,
              boundaryEventVariableScope,
              now,
              flowNodeInstanceProcessingContext.getFlowElements()));
      return true;
    }
    return false;
  }

  public boolean processEventCatchAll(
      ProcessInstanceProcessingContext processInstanceProcessingContext,
      FlowNodeInstanceProcessingContext flowNodeInstanceProcessingContext,
      I catchEventInstance,
      EventSignal event,
      DirectInstanceResult directInstanceResult,
      VariableScope variableScope) {
    long now = clock.millis();

    VariableScope boundaryEventVariableScope =
        variableScope.selectFlowNodeInstancesScope(catchEventInstance.getElementInstanceId());

    if (catchEventInstance.matchesEventCatchAll(event)) {
      InstanceResult instanceResult = processInstanceProcessingContext.getInstanceResult();
      getInstanceResultForContinue(
          processInstanceProcessingContext, directInstanceResult, catchEventInstance);
      ProcessInstance processInstance = processInstanceProcessingContext.getProcessInstance();
      selectNextNodeIfAllowedContinue(
          catchEventInstance,
          processInstance,
          boundaryEventVariableScope,
          flowNodeInstanceProcessingContext);
      instanceResult.addInstanceUpdate(
          createFlowNodeInstanceUpdate(
              processInstance,
              catchEventInstance,
              boundaryEventVariableScope,
              now,
              flowNodeInstanceProcessingContext.getFlowElements()));
      return true;
    }
    return false;
  }
}
