package nl.qunit.bpmnmeister.engine.pi.processor;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.Set;
import java.util.stream.Collectors;
import nl.qunit.bpmnmeister.engine.pd.CorrelationMessageSubscription;
import nl.qunit.bpmnmeister.engine.pd.MessageEvent;
import nl.qunit.bpmnmeister.engine.pd.SubscribeAction;
import nl.qunit.bpmnmeister.engine.pi.TriggerResult.TriggerResultBuilder;
import nl.qunit.bpmnmeister.engine.pi.feel.FeelExpressionHandler;
import nl.qunit.bpmnmeister.pd.model.IntermediateCatchEvent;
import nl.qunit.bpmnmeister.pd.model.Message;
import nl.qunit.bpmnmeister.pd.model.ProcessDefinition;
import nl.qunit.bpmnmeister.pi.ProcessInstance;
import nl.qunit.bpmnmeister.pi.Variables;
import nl.qunit.bpmnmeister.pi.state.FlowNodeStateEnum;
import nl.qunit.bpmnmeister.pi.state.IntermediateCatchEventState;
import nl.qunit.bpmnmeister.pi.state.IntermediateCatchEventState.IntermediateCatchEventStateBuilder;

@ApplicationScoped
public class MessageCatchEventHelper {

  @Inject FeelExpressionHandler feelExpressionHandler;

  public void processWhenReady(
      ProcessDefinition definition,
      TriggerResultBuilder triggerResultBuilder,
      IntermediateCatchEventStateBuilder<?, ?> newStateBuilder,
      ProcessInstance processInstance,
      IntermediateCatchEvent element,
      Variables variables) {

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
                      processInstance.getProcessInstanceKey(),
                      correlationKey,
                      element.getId(),
                      messageName,
                      SubscribeAction.SUBSSCRIBE);
                })
            .collect(Collectors.toSet());

    newStateBuilder.state(FlowNodeStateEnum.ACTIVE);
    triggerResultBuilder.newMessageSubscriptions(subscriptions);
  }

  public void processWhenActive(
      TriggerResultBuilder triggerResultBuilder,
      IntermediateCatchEventStateBuilder<?, ?> newStateBuilder,
      IntermediateCatchEvent element,
      IntermediateCatchEventState oldState) {

    triggerResultBuilder.newActiveFlows(element.getOutgoing());
    newStateBuilder.passedCnt(oldState.getPassedCnt() + 1);
    newStateBuilder.state(FlowNodeStateEnum.FINISHED);
  }
}
