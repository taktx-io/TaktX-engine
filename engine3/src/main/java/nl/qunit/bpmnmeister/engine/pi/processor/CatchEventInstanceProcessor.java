package nl.qunit.bpmnmeister.engine.pi.processor;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.NoArgsConstructor;
import nl.qunit.bpmnmeister.engine.pi.VariablesMapper;
import nl.qunit.bpmnmeister.pd.model.CatchEvent;
import nl.qunit.bpmnmeister.pd.model.EventSignal;
import nl.qunit.bpmnmeister.pd.model.FlowElements;
import nl.qunit.bpmnmeister.pd.model.InstanceResult;
import nl.qunit.bpmnmeister.pd.model.Message;
import nl.qunit.bpmnmeister.pd.model.NewCorrelationSubscriptionMessageEventInfo;
import nl.qunit.bpmnmeister.pd.model.ScheduledContinuationInfo;
import nl.qunit.bpmnmeister.pd.model.TerminateCorrelationSubscriptionMessageEventInfo;
import nl.qunit.bpmnmeister.pi.ContinueFlowElementTrigger;
import nl.qunit.bpmnmeister.pi.FeelExpressionHandler;
import nl.qunit.bpmnmeister.pi.FlowNodeInstances;
import nl.qunit.bpmnmeister.pi.Variables;
import nl.qunit.bpmnmeister.pi.instances.CatchEventInstance;
import nl.qunit.bpmnmeister.pi.state.CatchEventStateEnum;

@NoArgsConstructor
public abstract class CatchEventInstanceProcessor<
        E extends CatchEvent, I extends CatchEventInstance<? extends CatchEvent>>
    extends EventInstanceProcessor<E, I> {

  private FeelExpressionHandler feelExpressionHandler;

  protected CatchEventInstanceProcessor(
      IoMappingProcessor ioMappingProcessor,
      VariablesMapper variablesMapper,
      FeelExpressionHandler feelExpressionHandler) {
    super(ioMappingProcessor, variablesMapper);
    this.feelExpressionHandler = feelExpressionHandler;
  }

  @Override
  protected InstanceResult processStartSpecificEventInstance(
      FlowElements flowElements, I catchEventInstance, String inputFlowId, Variables variables) {
    InstanceResult result = new InstanceResult();

    catchEventInstance.setState(CatchEventStateEnum.FINISHED);

    catchEventInstance
        .getFlowNode()
        .getEscalationEventDefinitions()
        .forEach(
            escalationEventDefinition -> {
              catchEventInstance.setState(CatchEventStateEnum.WAITING);
              catchEventInstance.addEscalationSubscription(escalationEventDefinition);
            });

    catchEventInstance
        .getFlowNode()
        .getTimerEventDefinitions()
        .forEach(
            timerEventDefinition -> {
              catchEventInstance.setState(CatchEventStateEnum.WAITING);
              result.addNewScheduledContinuation(
                  new ScheduledContinuationInfo(
                      catchEventInstance, timerEventDefinition, variables));
            });

    catchEventInstance
        .getFlowNode()
        .getMessageventDefinitions()
        .forEach(
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
              result.addNewCorrelationSubcriptionMessageEvent(messageInfo);
            });

    return result;
  }

  @Override
  protected InstanceResult processContinueSpecificFlowNodeInstance(
      int subProcessLevel,
      FlowElements flowElements,
      I flowNodeInstance,
      ContinueFlowElementTrigger trigger,
      Variables variables,
      FlowNodeInstances flowNodeInstances) {
    return getInstanceResultForContinue(flowNodeInstance);
  }

  private InstanceResult getInstanceResultForContinue(I flowNodeInstance) {
    InstanceResult result = InstanceResult.empty();

    if (shouldCancel(flowNodeInstance)) {
      flowNodeInstance.setState(CatchEventStateEnum.FINISHED);
      terminateSubscriptions(flowNodeInstance, result);
    }
    result.merge(processContinueSpecificCatchEventInstance(flowNodeInstance));
    return result;
  }

  private void terminateSubscriptions(I flowNodeInstance, InstanceResult result) {
    terminateScheduleKeys(flowNodeInstance, result);
    terminateMessageSubscriptions(flowNodeInstance, result);
    terminateEscalationSubscriptions(flowNodeInstance, result);
  }

  private void terminateEscalationSubscriptions(I flowNodeInstance, InstanceResult result) {
    flowNodeInstance.clearEscalationSubscriptions();
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

  protected abstract InstanceResult processContinueSpecificCatchEventInstance(I flowNodeInstance);

  protected abstract boolean shouldCancel(I flowNodeInstance);

  @Override
  protected InstanceResult processTerminateSpecificFlowNodeInstance(
      I instance, Variables variables) {
    InstanceResult result = InstanceResult.empty();
    terminateSubscriptions(instance, result);
    return result;
  }

  public boolean processEvent(
      I catchEventInstance,
      EventSignal event,
      InstanceResult newInstanceResult,
      Variables variables,
      FlowNodeInstances flowNodeInstances) {
    if (catchEventInstance.matchesEvent(event)) {
      newInstanceResult.merge(getInstanceResultForContinue(catchEventInstance));
      selectNextNodeIfAllowedContinue(
          catchEventInstance, newInstanceResult, variables, false, flowNodeInstances);
      return true;
    }
    return false;
  }
}
