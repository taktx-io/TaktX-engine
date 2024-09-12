package nl.qunit.bpmnmeister.engine.pi.processor;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import nl.qunit.bpmnmeister.pd.model.FlowElements2;
import nl.qunit.bpmnmeister.pd.model.InstanceResult;
import nl.qunit.bpmnmeister.pd.model.NewCorrelationSubscriptionMessageEventInfo;
import nl.qunit.bpmnmeister.pd.model.ReceiveTask2;
import nl.qunit.bpmnmeister.pd.model.TerminateCorrelationSubscriptionMessageEventInfo;
import nl.qunit.bpmnmeister.pi.ContinueFlowElementTrigger2;
import nl.qunit.bpmnmeister.pi.FeelExpressionHandler;
import nl.qunit.bpmnmeister.pi.Variables2;
import nl.qunit.bpmnmeister.pi.instances.ReceiveTaskInstance;
import nl.qunit.bpmnmeister.pi.state.FlowNodeStateEnum;

@ApplicationScoped
public class ReceiveTaskInstanceProcessor
    extends ActivityInstanceProcessor<
        ReceiveTask2, ReceiveTaskInstance, ContinueFlowElementTrigger2> {
  private final FeelExpressionHandler feelExpressionHandler;

  @Inject
  public ReceiveTaskInstanceProcessor(
      FeelExpressionHandler feelExpressionHandler, IoMappingProcessor ioMappingProcessor) {
    super(ioMappingProcessor);
    this.feelExpressionHandler = feelExpressionHandler;
  }

  @Override
  protected InstanceResult processStartSpecificActivityInstance(
      FlowElements2 flowElements,
      ReceiveTask2 receiveTask,
      ReceiveTaskInstance receiveTaskInstance,
      Variables2 variables) {
    receiveTaskInstance.setState(FlowNodeStateEnum.WAITING);

    String correlationKeyExpression = receiveTask.getMessage().correlationKey();
    JsonNode jsonNode =
        feelExpressionHandler.processFeelExpression(correlationKeyExpression, variables);
    String correlationKey = jsonNode.asText();
    String messageName = receiveTask.getMessage().name();
    receiveTaskInstance.setCorrelationKey(correlationKey);
    InstanceResult instanceResult = InstanceResult.empty();
    instanceResult.addNewCorrelationSubcriptionMessageEvent(
        new NewCorrelationSubscriptionMessageEventInfo(
            messageName, correlationKey, receiveTask, receiveTaskInstance));
    return instanceResult;
  }

  @Override
  protected InstanceResult processContinueSpecificActivityInstance(
      int subProcessLevel,
      FlowElements2 flowElements,
      ReceiveTask2 receiveTask,
      ReceiveTaskInstance receiveTaskInstance,
      ContinueFlowElementTrigger2 trigger,
      Variables2 processInstanceVariables) {
    receiveTaskInstance.setState(FlowNodeStateEnum.FINISHED);
    return terminatingSubscriptionInstanceResult(receiveTask, receiveTaskInstance);
  }

  @Override
  protected InstanceResult processTerminateSpecificActivityInstance(
      ReceiveTask2 receiveTask, ReceiveTaskInstance instance) {
    return terminatingSubscriptionInstanceResult(receiveTask, instance);
  }

  private static InstanceResult terminatingSubscriptionInstanceResult(
      ReceiveTask2 receiveTask, ReceiveTaskInstance receiveTaskInstance) {
    String messageName = receiveTask.getMessage().name();
    InstanceResult instanceResult = InstanceResult.empty();
    instanceResult.addTerminateCorrelationSubscriptionMessageEvent(
        new TerminateCorrelationSubscriptionMessageEventInfo(
            messageName, receiveTaskInstance.getCorrelationKey()));
    return instanceResult;
  }
}
