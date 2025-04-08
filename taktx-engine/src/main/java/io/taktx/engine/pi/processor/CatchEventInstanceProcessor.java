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
import io.taktx.dto.v_1_0_0.CatchEventStateEnum;
import io.taktx.dto.v_1_0_0.ContinueFlowElementTriggerDTO;
import io.taktx.engine.feel.FeelExpressionHandler;
import io.taktx.engine.pd.model.CatchEvent;
import io.taktx.engine.pd.model.EventSignal;
import io.taktx.engine.pd.model.FlowElements;
import io.taktx.engine.pd.model.Message;
import io.taktx.engine.pi.DirectInstanceResult;
import io.taktx.engine.pi.InstanceResult;
import io.taktx.engine.pi.ProcessInstanceMapper;
import io.taktx.engine.pi.ProcessingContext;
import io.taktx.engine.pi.model.CatchEventInstance;
import io.taktx.engine.pi.model.FlowNodeInstances;
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
      ProcessingContext processingContext,
      DirectInstanceResult directInstanceResult,
      FlowElements flowElements,
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
                processingContext
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
              processingContext
                  .getInstanceResult()
                  .addNewCorrelationSubcriptionMessageEvent(messageInfo);
            });
  }

  protected abstract boolean shoudHandleTimerxEvents();

  @Override
  protected void processContinueSpecificFlowNodeInstance(
      ProcessingContext processingContext,
      DirectInstanceResult directInstanceResult,
      int subProcessLevel,
      FlowElements flowElements,
      I flowNodeInstance,
      ContinueFlowElementTriggerDTO trigger,
      VariableScope variables,
      FlowNodeInstances flowNodeInstances) {
    getInstanceResultForContinue(processingContext, directInstanceResult, flowNodeInstance);
  }

  private void getInstanceResultForContinue(
      ProcessingContext processingContext,
      DirectInstanceResult directInstanceResult,
      I flowNodeInstance) {

    if (shouldCancel(flowNodeInstance)) {
      flowNodeInstance.setState(CatchEventStateEnum.FINISHED);
      terminateSubscriptions(flowNodeInstance, processingContext.getInstanceResult());
    }
    processContinueSpecificCatchEventInstance(
        processingContext, directInstanceResult, flowNodeInstance);
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
      ProcessingContext processingContext,
      DirectInstanceResult directInstanceResult,
      I flowNodeInstance);

  protected abstract boolean shouldCancel(I flowNodeInstance);

  @Override
  protected void processTerminateSpecificFlowNodeInstance(
      ProcessingContext processingContext,
      DirectInstanceResult directInstanceResult,
      I instance,
      VariableScope currentVariableScope,
      FlowElements flowElements) {
    terminateSubscriptions(instance, processingContext.getInstanceResult());
  }

  public boolean processEvent(
      ProcessingContext processingContext,
      I catchEventInstance,
      EventSignal event,
      DirectInstanceResult directInstanceResult,
      VariableScope parentVariableScope,
      FlowNodeInstances flowNodeInstances,
      FlowElements flowElements) {
    long now = clock.millis();
    VariableScope boundaryEventVariableScope =
        parentVariableScope.selectFlowNodeInstancesScope(catchEventInstance.getElementInstanceId());

    InstanceResult newInstanceResult = processingContext.getInstanceResult();
    if (catchEventInstance.matchesEvent(event)) {
      getInstanceResultForContinue(processingContext, directInstanceResult, catchEventInstance);
      ProcessInstance processInstance = processingContext.getProcessInstance();
      selectNextNodeIfAllowedContinue(
          catchEventInstance,
          processInstance,
          directInstanceResult,
          boundaryEventVariableScope,
          flowNodeInstances);
      newInstanceResult.addInstanceUpdate(
          createFlowNodeInstanceUpdate(
              processInstance, catchEventInstance, boundaryEventVariableScope, now, flowElements));
      return true;
    }
    return false;
  }

  public boolean processEventCatchAll(
      ProcessingContext processingContext,
      I catchEventInstance,
      EventSignal event,
      DirectInstanceResult directInstanceResult,
      VariableScope variableScope,
      FlowNodeInstances flowNodeInstances,
      FlowElements flowElements) {
    long now = clock.millis();

    VariableScope boundaryEventVariableScope =
        variableScope.selectFlowNodeInstancesScope(catchEventInstance.getElementInstanceId());

    if (catchEventInstance.matchesEventCatchAll(event)) {
      InstanceResult instanceResult = processingContext.getInstanceResult();
      getInstanceResultForContinue(processingContext, directInstanceResult, catchEventInstance);
      ProcessInstance processInstance = processingContext.getProcessInstance();
      selectNextNodeIfAllowedContinue(
          catchEventInstance,
          processInstance,
          directInstanceResult,
          boundaryEventVariableScope,
          flowNodeInstances);
      instanceResult.addInstanceUpdate(
          createFlowNodeInstanceUpdate(
              processInstance, catchEventInstance, boundaryEventVariableScope, now, flowElements));
      return true;
    }
    return false;
  }
}
