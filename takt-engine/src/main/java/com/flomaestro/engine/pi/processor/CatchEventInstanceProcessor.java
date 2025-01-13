package com.flomaestro.engine.pi.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.flomaestro.engine.feel.FeelExpressionHandler;
import com.flomaestro.engine.pd.model.CatchEvent;
import com.flomaestro.engine.pd.model.EventSignal;
import com.flomaestro.engine.pd.model.FlowElements;
import com.flomaestro.engine.pd.model.Message;
import com.flomaestro.engine.pi.DirectInstanceResult;
import com.flomaestro.engine.pi.InstanceResult;
import com.flomaestro.engine.pi.ProcessInstanceMapper;
import com.flomaestro.engine.pi.ProcessingStatistics;
import com.flomaestro.engine.pi.VariablesMapper;
import com.flomaestro.engine.pi.model.CatchEventInstance;
import com.flomaestro.engine.pi.model.FlowNodeInstances;
import com.flomaestro.engine.pi.model.NewCorrelationSubscriptionMessageEventInfo;
import com.flomaestro.engine.pi.model.ProcessInstance;
import com.flomaestro.engine.pi.model.ScheduledContinuationInfo;
import com.flomaestro.engine.pi.model.TerminateCorrelationSubscriptionMessageEventInfo;
import com.flomaestro.engine.pi.model.Variables;
import com.flomaestro.takt.dto.v_1_0_0.CatchEventStateEnum;
import com.flomaestro.takt.dto.v_1_0_0.ContinueFlowElementTriggerDTO;
import java.time.Clock;
import lombok.NoArgsConstructor;

@NoArgsConstructor
public abstract class CatchEventInstanceProcessor<
        E extends CatchEvent, I extends CatchEventInstance<? extends CatchEvent>>
    extends EventInstanceProcessor<E, I> {

  private FeelExpressionHandler feelExpressionHandler;

  protected CatchEventInstanceProcessor(
      IoMappingProcessor ioMappingProcessor,
      VariablesMapper variablesMapper,
      ProcessInstanceMapper processInstanceMapper,
      FeelExpressionHandler feelExpressionHandler,
      Clock clock) {
    super(ioMappingProcessor, processInstanceMapper, variablesMapper, clock);
    this.feelExpressionHandler = feelExpressionHandler;
  }

  @Override
  protected void processStartSpecificEventInstance(
      ProcessInstance processInstance,
      InstanceResult instanceResult,
      DirectInstanceResult directInstanceResult,
      FlowElements flowElements,
      I catchEventInstance,
      String inputFlowId,
      Variables variables,
      ProcessingStatistics processingStatistics) {

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
                instanceResult.addNewScheduledContinuation(
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
              instanceResult.addNewCorrelationSubcriptionMessageEvent(messageInfo);
            });
  }

  protected abstract boolean shoudHandleTimerxEvents();

  @Override
  protected void processContinueSpecificFlowNodeInstance(
      InstanceResult instanceResult,
      DirectInstanceResult directInstanceResult,
      int subProcessLevel,
      FlowElements flowElements,
      ProcessInstance processInstance,
      I flowNodeInstance,
      ContinueFlowElementTriggerDTO trigger,
      Variables variables,
      FlowNodeInstances flowNodeInstances,
      ProcessingStatistics processingStatistics) {
    getInstanceResultForContinue(
        instanceResult, directInstanceResult, flowNodeInstance, processingStatistics);
  }

  private void getInstanceResultForContinue(
      InstanceResult instanceResult,
      DirectInstanceResult directInstanceResult,
      I flowNodeInstance,
      ProcessingStatistics processingStatistics) {

    if (shouldCancel(flowNodeInstance)) {
      flowNodeInstance.setState(CatchEventStateEnum.FINISHED);
      terminateSubscriptions(flowNodeInstance, instanceResult);
    }
    processContinueSpecificCatchEventInstance(
        instanceResult, directInstanceResult, flowNodeInstance, processingStatistics);
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
      InstanceResult instanceResult,
      DirectInstanceResult directInstanceResult,
      I flowNodeInstance,
      ProcessingStatistics processingStatistics);

  protected abstract boolean shouldCancel(I flowNodeInstance);

  @Override
  protected void processTerminateSpecificFlowNodeInstance(
      InstanceResult instanceResult,
      DirectInstanceResult directInstanceResult,
      I instance,
      ProcessInstance processInstance,
      Variables variables,
      ProcessingStatistics processingStatistics) {
    terminateSubscriptions(instance, instanceResult);
  }

  public boolean processEvent(
      I catchEventInstance,
      EventSignal event,
      InstanceResult newInstanceResult,
      DirectInstanceResult directInstanceResult,
      Variables variables,
      ProcessInstance processInstance,
      FlowNodeInstances flowNodeInstances,
      ProcessingStatistics processingStatistics) {
    long now = clock.millis();
    if (catchEventInstance.matchesEvent(event)) {
      catchEventInstance.setDirty();
      getInstanceResultForContinue(
          newInstanceResult, directInstanceResult, catchEventInstance, processingStatistics);
      selectNextNodeIfAllowedContinue(
          catchEventInstance,
          processInstance,
          directInstanceResult,
          variables,
          false,
          flowNodeInstances);
      newInstanceResult.addInstanceUpdate(
          createFlowNodeInstanceUpdate(
              processInstance,
              flowNodeInstances.getFlowNodeInstancesId(),
              catchEventInstance,
              variables,
              now));
      return true;
    }
    return false;
  }

  public boolean processEventCatchAll(
      I catchEventInstance,
      EventSignal event,
      InstanceResult instanceResult,
      DirectInstanceResult directInstanceResult,
      Variables variables,
      ProcessInstance processInstance,
      FlowNodeInstances flowNodeInstances,
      ProcessingStatistics processingStatistics) {
    long now = clock.millis();
    if (catchEventInstance.matchesEventCatchAll(event)) {
      getInstanceResultForContinue(
          instanceResult, directInstanceResult, catchEventInstance, processingStatistics);
      selectNextNodeIfAllowedContinue(
          catchEventInstance,
          processInstance,
          directInstanceResult,
          variables,
          false,
          flowNodeInstances);
      instanceResult.addInstanceUpdate(
          createFlowNodeInstanceUpdate(
              processInstance,
              flowNodeInstances.getFlowNodeInstancesId(),
              catchEventInstance,
              variables,
              now));
      return true;
    }
    return false;
  }
}
