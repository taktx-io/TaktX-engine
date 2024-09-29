package nl.qunit.bpmnmeister.engine.pi.processor;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import nl.qunit.bpmnmeister.engine.pi.VariablesMapper;
import nl.qunit.bpmnmeister.pd.model.FlowElements2;
import nl.qunit.bpmnmeister.pd.model.InstanceResult;
import nl.qunit.bpmnmeister.pd.model.NewCorrelationSubscriptionMessageEventInfo;
import nl.qunit.bpmnmeister.pd.model.ReceiveTask2;
import nl.qunit.bpmnmeister.pd.model.TerminateCorrelationSubscriptionMessageEventInfo;
import nl.qunit.bpmnmeister.pi.ContinueFlowElementTrigger2;
import nl.qunit.bpmnmeister.pi.FeelExpressionHandler;
import nl.qunit.bpmnmeister.pi.Variables2;
import nl.qunit.bpmnmeister.pi.instances.ReceiveTaskInstance;
import nl.qunit.bpmnmeister.pi.state.ActtivityStateEnum;

@ApplicationScoped
public class ReceiveTaskInstanceProcessor
    extends ActivityInstanceProcessor<
        ReceiveTask2, ReceiveTaskInstance, ContinueFlowElementTrigger2> {
  private final FeelExpressionHandler feelExpressionHandler;

  @Inject
  public ReceiveTaskInstanceProcessor(
      FeelExpressionHandler feelExpressionHandler,
      IoMappingProcessor ioMappingProcessor,
      VariablesMapper variablesMapper) {
    super(ioMappingProcessor, variablesMapper);
    this.feelExpressionHandler = feelExpressionHandler;
  }

  @Override
  protected InstanceResult processStartSpecificActivityInstance(
      FlowElements2 flowElements,
      ReceiveTaskInstance receiveTaskInstance,
      String inputFlowId,
      Variables2 variables) {
    receiveTaskInstance.setState(ActtivityStateEnum.WAITING);

    ReceiveTask2 receiveTask = receiveTaskInstance.getFlowNode();
    String correlationKeyExpression = receiveTask.getReferencedMessage().correlationKey();
    JsonNode jsonNode =
        feelExpressionHandler.processFeelExpression(correlationKeyExpression, variables);
    String correlationKey = jsonNode.asText();
    String messageName = receiveTask.getReferencedMessage().name();
    receiveTaskInstance.setCorrelationKey(correlationKey);
    InstanceResult instanceResult = InstanceResult.empty();
    instanceResult.addNewCorrelationSubcriptionMessageEvent(
        new NewCorrelationSubscriptionMessageEventInfo(
            messageName, correlationKey, receiveTaskInstance));
    return instanceResult;
  }

  @Override
  protected InstanceResult processContinueSpecificActivityInstance(
      int subProcessLevel,
      FlowElements2 flowElements,
      ReceiveTaskInstance receiveTaskInstance,
      ContinueFlowElementTrigger2 trigger,
      Variables2 processInstanceVariables) {
    receiveTaskInstance.setState(ActtivityStateEnum.FINISHED);
    return terminatingSubscriptionInstanceResult(receiveTaskInstance);
  }

  @Override
  protected InstanceResult processTerminateSpecificActivityInstance(ReceiveTaskInstance instance) {
    return terminatingSubscriptionInstanceResult(instance);
  }

  private static InstanceResult terminatingSubscriptionInstanceResult(
      ReceiveTaskInstance receiveTaskInstance) {
    String messageName = receiveTaskInstance.getFlowNode().getReferencedMessage().name();
    InstanceResult instanceResult = InstanceResult.empty();
    instanceResult.addTerminateCorrelationSubscriptionMessageEvent(
        new TerminateCorrelationSubscriptionMessageEventInfo(
            messageName, receiveTaskInstance.getCorrelationKey()));
    return instanceResult;
  }
}
