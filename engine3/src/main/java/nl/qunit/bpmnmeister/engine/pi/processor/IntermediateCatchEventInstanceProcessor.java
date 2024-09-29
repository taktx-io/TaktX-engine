package nl.qunit.bpmnmeister.engine.pi.processor;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.NoArgsConstructor;
import nl.qunit.bpmnmeister.engine.pi.VariablesMapper;
import nl.qunit.bpmnmeister.pd.model.FlowElements2;
import nl.qunit.bpmnmeister.pd.model.InstanceResult;
import nl.qunit.bpmnmeister.pd.model.IntermediateCatchEvent2;
import nl.qunit.bpmnmeister.pd.model.Message2;
import nl.qunit.bpmnmeister.pd.model.NewCorrelationSubscriptionMessageEventInfo;
import nl.qunit.bpmnmeister.pd.model.ScheduledContinuationInfo;
import nl.qunit.bpmnmeister.pi.ContinueFlowElementTrigger2;
import nl.qunit.bpmnmeister.pi.FeelExpressionHandler;
import nl.qunit.bpmnmeister.pi.FlowNodeStates2;
import nl.qunit.bpmnmeister.pi.Variables2;
import nl.qunit.bpmnmeister.pi.instances.IntermediateCatchEventInstance;
import nl.qunit.bpmnmeister.pi.state.IntermediateCatchEventStateEnum;

@ApplicationScoped
@NoArgsConstructor
public class IntermediateCatchEventInstanceProcessor
    extends CatchEventInstanceProcessor<IntermediateCatchEvent2, IntermediateCatchEventInstance> {

  private FeelExpressionHandler feelExpressionHandler;

  @Inject
  IntermediateCatchEventInstanceProcessor(
      IoMappingProcessor ioMappingProcessor,
      FeelExpressionHandler feelExpressionHandler,
      VariablesMapper variablesMapper) {
    super(ioMappingProcessor, variablesMapper);
    this.feelExpressionHandler = feelExpressionHandler;
  }

  @Override
  protected InstanceResult processStartSpecificCatchEventInstance(
      FlowElements2 flowElements,
      IntermediateCatchEventInstance flowNodeInstance,
      Variables2 variables) {
    InstanceResult result = new InstanceResult();
    flowNodeInstance.setState(IntermediateCatchEventStateEnum.WAITING);

    flowNodeInstance
        .getFlowNode()
        .getTimerEventDefinitions()
        .forEach(
            timerEventDefinition -> {
              result.addNewScheduledContinuation(
                  new ScheduledContinuationInfo(flowNodeInstance, timerEventDefinition, variables));
            });

    flowNodeInstance.getFlowNode().getMessageventDefinitions().stream()
        .forEach(
            messageEventDefinition -> {
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
      IntermediateCatchEventInstance flowNodeInstance,
      ContinueFlowElementTrigger2 trigger,
      Variables2 variables,
      FlowNodeStates2 flowNodeStates) {
    flowNodeInstance.setState(IntermediateCatchEventStateEnum.FINISHED);
    return InstanceResult.empty();
  }

  @Override
  protected InstanceResult processTerminateSpecificFlowNodeInstance(
      IntermediateCatchEventInstance instance) {
    return null;
  }
}
