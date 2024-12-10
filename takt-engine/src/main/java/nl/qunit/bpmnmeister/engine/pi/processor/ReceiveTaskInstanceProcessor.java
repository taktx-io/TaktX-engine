package nl.qunit.bpmnmeister.engine.pi.processor;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import nl.qunit.bpmnmeister.engine.feel.FeelExpressionHandler;
import nl.qunit.bpmnmeister.engine.pd.model.FlowElements;
import nl.qunit.bpmnmeister.engine.pd.model.ReceiveTask;
import nl.qunit.bpmnmeister.engine.pi.DirectInstanceResult;
import nl.qunit.bpmnmeister.engine.pi.InstanceResult;
import nl.qunit.bpmnmeister.engine.pi.ProcessInstanceMapper;
import nl.qunit.bpmnmeister.engine.pi.VariablesMapper;
import nl.qunit.bpmnmeister.engine.pi.model.NewCorrelationSubscriptionMessageEventInfo;
import nl.qunit.bpmnmeister.engine.pi.model.ProcessInstance;
import nl.qunit.bpmnmeister.engine.pi.model.ReceiveTaskInstance;
import nl.qunit.bpmnmeister.engine.pi.model.TerminateCorrelationSubscriptionMessageEventInfo;
import nl.qunit.bpmnmeister.pi.ContinueFlowElementTriggerDTO;
import nl.qunit.bpmnmeister.pi.Variables;
import nl.qunit.bpmnmeister.pi.state.ActtivityStateEnum;

@ApplicationScoped
public class ReceiveTaskInstanceProcessor
    extends ActivityInstanceProcessor<
    ReceiveTask, ReceiveTaskInstance, ContinueFlowElementTriggerDTO> {
  private final FeelExpressionHandler feelExpressionHandler;

  @Inject
  public ReceiveTaskInstanceProcessor(
      FeelExpressionHandler feelExpressionHandler,
      IoMappingProcessor ioMappingProcessor,
      ProcessInstanceMapper processInstanceMapper,
      VariablesMapper variablesMapper) {
    super(ioMappingProcessor, processInstanceMapper, variablesMapper);
    this.feelExpressionHandler = feelExpressionHandler;
  }

  @Override
  protected void processStartSpecificActivityInstance(
      InstanceResult instanceResult,
      DirectInstanceResult directInstanceResult,
      FlowElements flowElements,
      ReceiveTaskInstance receiveTaskInstance,
      ProcessInstance processInstance,
      String inputFlowId,
      Variables variables) {
    receiveTaskInstance.setState(ActtivityStateEnum.WAITING);

    ReceiveTask receiveTask = receiveTaskInstance.getFlowNode();
    String correlationKeyExpression = receiveTask.getReferencedMessage().correlationKey();
    JsonNode jsonNode =
        feelExpressionHandler.processFeelExpression(correlationKeyExpression, variables);
    String correlationKey = jsonNode.asText();
    String messageName = receiveTask.getReferencedMessage().name();
    receiveTaskInstance.setCorrelationKey(correlationKey);
    instanceResult.addNewCorrelationSubcriptionMessageEvent(
        new NewCorrelationSubscriptionMessageEventInfo(
            messageName, correlationKey, receiveTaskInstance));
  }

  @Override
  protected void processContinueSpecificActivityInstance(
      InstanceResult instanceResult,
      DirectInstanceResult directInstanceResult,
      int subProcessLevel,
      FlowElements flowElements,
      ProcessInstance processInstance,
      ReceiveTaskInstance receiveTaskInstance,
      ContinueFlowElementTriggerDTO trigger,
      Variables processInstanceVariables) {
    receiveTaskInstance.setState(ActtivityStateEnum.FINISHED);
    terminatingSubscriptionInstanceResult(instanceResult, receiveTaskInstance);
  }

  @Override
  protected void processTerminateSpecificActivityInstance(
      InstanceResult instanceResult,
      DirectInstanceResult directInstanceResult,
      ReceiveTaskInstance instance,
      ProcessInstance processInstance,
      Variables processInstanceVariables) {
    terminatingSubscriptionInstanceResult(instanceResult, instance);
  }

  private static void terminatingSubscriptionInstanceResult(
      InstanceResult instanceResult, ReceiveTaskInstance receiveTaskInstance) {
    String messageName = receiveTaskInstance.getFlowNode().getReferencedMessage().name();
    instanceResult.addTerminateCorrelationSubscriptionMessageEvent(
        new TerminateCorrelationSubscriptionMessageEventInfo(
            messageName, receiveTaskInstance.getCorrelationKey()));
  }
}
