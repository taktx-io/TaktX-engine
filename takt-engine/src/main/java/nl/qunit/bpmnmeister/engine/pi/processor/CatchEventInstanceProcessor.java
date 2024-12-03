package nl.qunit.bpmnmeister.engine.pi.processor;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.NoArgsConstructor;
import nl.qunit.bpmnmeister.engine.pi.DirectInstanceResult;
import nl.qunit.bpmnmeister.engine.pi.InstanceResult;
import nl.qunit.bpmnmeister.engine.pi.ProcessInstanceMapper;
import nl.qunit.bpmnmeister.engine.pi.VariablesMapper;
import nl.qunit.bpmnmeister.pd.model.CatchEvent;
import nl.qunit.bpmnmeister.pd.model.EventSignal;
import nl.qunit.bpmnmeister.pd.model.FlowElements;
import nl.qunit.bpmnmeister.pd.model.Message;
import nl.qunit.bpmnmeister.pd.model.NewCorrelationSubscriptionMessageEventInfo;
import nl.qunit.bpmnmeister.pd.model.ScheduledContinuationInfo;
import nl.qunit.bpmnmeister.pd.model.TerminateCorrelationSubscriptionMessageEventInfo;
import nl.qunit.bpmnmeister.pi.ContinueFlowElementTrigger;
import nl.qunit.bpmnmeister.pi.FeelExpressionHandler;
import nl.qunit.bpmnmeister.pi.FlowNodeInstances;
import nl.qunit.bpmnmeister.pi.ProcessInstance;
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
      ProcessInstanceMapper processInstanceMapper,
      FeelExpressionHandler feelExpressionHandler) {
    super(ioMappingProcessor, processInstanceMapper, variablesMapper);
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
      Variables variables) {

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
        .getErrorEventDefinitions()
        .forEach(
            errorEventDefinition -> {
              catchEventInstance.setState(CatchEventStateEnum.WAITING);
              catchEventInstance.addErrorSubscription(errorEventDefinition);
            });

    if (shoudHandleTimerxEvents()) {
      catchEventInstance
          .getFlowNode()
          .getTimerEventDefinitions()
          .forEach(
              timerEventDefinition -> {
                catchEventInstance.setState(CatchEventStateEnum.WAITING);
                instanceResult.addNewScheduledContinuation(
                    new ScheduledContinuationInfo(
                        catchEventInstance, timerEventDefinition, variables));
              });
    }
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
      ContinueFlowElementTrigger trigger,
      Variables variables,
      FlowNodeInstances flowNodeInstances) {
    getInstanceResultForContinue(instanceResult, directInstanceResult, flowNodeInstance);
  }

  private void getInstanceResultForContinue(
      InstanceResult instanceResult,
      DirectInstanceResult directInstanceResult,
      I flowNodeInstance) {

    if (shouldCancel(flowNodeInstance)) {
      flowNodeInstance.setState(CatchEventStateEnum.FINISHED);
      terminateSubscriptions(flowNodeInstance, instanceResult);
    }
    processContinueSpecificCatchEventInstance(
        instanceResult, directInstanceResult, flowNodeInstance);
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
      InstanceResult instanceResult, DirectInstanceResult directInstanceResult, I flowNodeInstance);

  protected abstract boolean shouldCancel(I flowNodeInstance);

  @Override
  protected void processTerminateSpecificFlowNodeInstance(
      InstanceResult instanceResult,
      DirectInstanceResult directInstanceResult,
      I instance,
      ProcessInstance processInstance,
      Variables variables) {
    terminateSubscriptions(instance, instanceResult);
  }

  public boolean processEvent(
      I catchEventInstance,
      EventSignal event,
      InstanceResult newInstanceResult,
      DirectInstanceResult directInstanceResult,
      Variables variables,
      ProcessInstance processInstance,
      FlowNodeInstances flowNodeInstances) {
    if (catchEventInstance.matchesEvent(event)) {
      getInstanceResultForContinue(newInstanceResult, directInstanceResult, catchEventInstance);
      selectNextNodeIfAllowedContinue(
          catchEventInstance,
          processInstance,
          directInstanceResult,
          variables,
          false,
          flowNodeInstances);
      newInstanceResult.addProcessInstanceUpdate(
          createFlowNodeInstanceUpdate(
              processInstance,
              flowNodeInstances.getFlowNodeInstancesId(),
              catchEventInstance,
              variables));
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
      FlowNodeInstances flowNodeInstances) {
    if (catchEventInstance.matchesEventCatchAll(event)) {
      getInstanceResultForContinue(instanceResult, directInstanceResult, catchEventInstance);
      selectNextNodeIfAllowedContinue(
          catchEventInstance,
          processInstance,
          directInstanceResult,
          variables,
          false,
          flowNodeInstances);
      instanceResult.addProcessInstanceUpdate(
          createFlowNodeInstanceUpdate(
              processInstance,
              flowNodeInstances.getFlowNodeInstancesId(),
              catchEventInstance,
              variables));
      return true;
    }
    return false;
  }
}
