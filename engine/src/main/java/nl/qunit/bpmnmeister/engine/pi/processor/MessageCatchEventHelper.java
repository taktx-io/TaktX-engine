package nl.qunit.bpmnmeister.engine.pi.processor;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import nl.qunit.bpmnmeister.engine.pi.ScopedVars;
import nl.qunit.bpmnmeister.engine.pi.TriggerResult.TriggerResultBuilder;
import nl.qunit.bpmnmeister.engine.pi.feel.FeelExpressionHandler;
import nl.qunit.bpmnmeister.pd.model.IntermediateCatchEvent;
import nl.qunit.bpmnmeister.pd.model.Message;
import nl.qunit.bpmnmeister.pd.model.ProcessDefinition;
import nl.qunit.bpmnmeister.pi.ContinueFlowElementTrigger;
import nl.qunit.bpmnmeister.pi.CorrelationMessageSubscription;
import nl.qunit.bpmnmeister.pi.ProcessInstance;
import nl.qunit.bpmnmeister.pi.Variables;
import nl.qunit.bpmnmeister.pi.state.FlowNodeStateEnum;
import nl.qunit.bpmnmeister.pi.state.IntermediateCatchEventState;
import nl.qunit.bpmnmeister.pi.state.IntermediateCatchEventState.IntermediateCatchEventStateBuilder;
import nl.qunit.bpmnmeister.pi.state.MessageEvent;

@ApplicationScoped
public class MessageCatchEventHelper {

  @Inject FeelExpressionHandler feelExpressionHandler;

  @Inject IoMappingProcessor ioMappingProcessor;

  public void processWhenReady(
      ProcessDefinition definition,
      TriggerResultBuilder triggerResultBuilder,
      IntermediateCatchEventStateBuilder<?, ?> newStateBuilder,
      ProcessInstance processInstance,
      IntermediateCatchEvent element,
      ScopedVars variables) {

    Set<MessageEvent> subscriptions =
        element.getMessageventDefinitions().stream()
            .map(
                messageEventDefinition -> {
                  Message message =
                      definition
                          .getDefinitions()
                          .getMessages()
                          .get(messageEventDefinition.getMessageRef());
                  String correlationKeyExpression = message.getCorrelationKey();
                  JsonNode jsonNode =
                      feelExpressionHandler.processFeelExpression(
                          correlationKeyExpression, variables);
                  String correlationKey = jsonNode.asText();
                  String messageName = message.getName();
                  return new CorrelationMessageSubscription(
                      processInstance.getRootInstanceKey(),
                      processInstance.getProcessInstanceKey(),
                      correlationKey,
                      element.getId(),
                      newStateBuilder.build().getElementInstanceId(),
                      messageName);
                })
            .collect(Collectors.toSet());

    newStateBuilder.state(FlowNodeStateEnum.ACTIVE);
    triggerResultBuilder.newMessageSubscriptions(subscriptions);
  }

  public void processWhenActive(
      ContinueFlowElementTrigger trigger,
      TriggerResultBuilder triggerResultBuilder,
      IntermediateCatchEventStateBuilder<?, ?> newStateBuilder,
      IntermediateCatchEvent element,
      IntermediateCatchEventState oldState,
      ProcessInstance processInstance,
      ProcessDefinition processDefinition,
      ScopedVars variables) {

    UUID childProcessInstanceKey = UUID.randomUUID();
    variables.push(
        childProcessInstanceKey, processInstance.getProcessInstanceKey(), trigger.getVariables());
    Variables outputVariables = ioMappingProcessor.getOutputVariables(element, variables);
    variables.pop();
    variables.merge(outputVariables);

    triggerResultBuilder.processInstanceTriggers(
        TriggerHelper.getProcessInstanceTriggersForOutputFlows(
            processInstance, processDefinition, oldState, element));
    newStateBuilder.passedCnt(oldState.getPassedCnt() + 1);
    newStateBuilder.state(FlowNodeStateEnum.FINISHED);
  }
}
