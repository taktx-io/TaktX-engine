package nl.qunit.bpmnmeister.engine.pi.processor;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.NoArgsConstructor;
import nl.qunit.bpmnmeister.engine.pi.VariablesMapper;
import nl.qunit.bpmnmeister.pd.model.CatchEvent2;
import nl.qunit.bpmnmeister.pd.model.FlowElements2;
import nl.qunit.bpmnmeister.pd.model.InstanceResult;
import nl.qunit.bpmnmeister.pd.model.Message2;
import nl.qunit.bpmnmeister.pd.model.NewCorrelationSubscriptionMessageEventInfo;
import nl.qunit.bpmnmeister.pd.model.ScheduledContinuationInfo;
import nl.qunit.bpmnmeister.pd.model.TerminateCorrelationSubscriptionMessageEventInfo;
import nl.qunit.bpmnmeister.pi.ContinueFlowElementTrigger2;
import nl.qunit.bpmnmeister.pi.FeelExpressionHandler;
import nl.qunit.bpmnmeister.pi.FlowNodeStates2;
import nl.qunit.bpmnmeister.pi.Variables2;
import nl.qunit.bpmnmeister.pi.instances.CatchEventInstance;
import nl.qunit.bpmnmeister.pi.state.CatchEventStateEnum;

@NoArgsConstructor
public abstract class CatchEventInstanceProcessor<
        E extends CatchEvent2, I extends CatchEventInstance<? extends CatchEvent2>>
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
      FlowElements2 flowElements, I flowNodeInstance, String inputFlowId, Variables2 variables) {
    InstanceResult result = new InstanceResult();

    flowNodeInstance.setState(CatchEventStateEnum.FINISHED);

    flowNodeInstance
        .getFlowNode()
        .getTimerEventDefinitions()
        .forEach(
            timerEventDefinition -> {
              flowNodeInstance.setState(CatchEventStateEnum.WAITING);
              result.addNewScheduledContinuation(
                  new ScheduledContinuationInfo(flowNodeInstance, timerEventDefinition, variables));
            });

    flowNodeInstance
        .getFlowNode()
        .getMessageventDefinitions()
        .forEach(
            messageEventDefinition -> {
              flowNodeInstance.setState(CatchEventStateEnum.WAITING);
              Message2 message = messageEventDefinition.getReferencedMessage();
              String correlationKeyExpression = message.correlationKey();
              JsonNode jsonNode =
                  feelExpressionHandler.processFeelExpression(correlationKeyExpression, variables);
              String correlationKey = jsonNode.asText();
              String messageName = message.name();
              NewCorrelationSubscriptionMessageEventInfo messageInfo =
                  new NewCorrelationSubscriptionMessageEventInfo(
                      messageName, correlationKey, flowNodeInstance);
              result.addNewCorrelationSubcriptionMessageEvent(messageInfo);
            });

    return result;
  }

  @Override
  protected InstanceResult processContinueSpecificFlowNodeInstance(
      int subProcessLevel,
      FlowElements2 flowElements,
      I flowNodeInstance,
      ContinueFlowElementTrigger2 trigger,
      Variables2 variables,
      FlowNodeStates2 flowNodeStates) {
    flowNodeInstance.setState(CatchEventStateEnum.FINISHED);
    InstanceResult result = InstanceResult.empty();
    if (shoudCancelSchedulesAndScubscriptions()) {
      flowNodeInstance.getScheduledKeys().forEach(result::cancelSchedule);

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
    return result;
  }

  protected abstract boolean shoudCancelSchedulesAndScubscriptions();

  @Override
  protected InstanceResult processTerminateSpecificFlowNodeInstance(I instance) {
    return InstanceResult.empty();
  }
}
