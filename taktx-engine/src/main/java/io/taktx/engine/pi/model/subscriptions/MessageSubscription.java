package io.taktx.engine.pi.model.subscriptions;

import com.fasterxml.jackson.databind.JsonNode;
import io.taktx.dto.subscriptions.SubScriptionType;
import io.taktx.engine.feel.FeelExpressionHandler;
import io.taktx.engine.pd.model.EventSignal;
import io.taktx.engine.pd.model.FlowNode;
import io.taktx.engine.pd.model.Message;
import io.taktx.engine.pi.ProcessInstanceException;
import io.taktx.engine.pi.ProcessInstanceProcessingContext;
import io.taktx.engine.pi.model.FlowNodeInstance;
import io.taktx.engine.pi.model.IFlowNodeInstance;
import io.taktx.engine.pi.model.MessageEventSignal;
import io.taktx.engine.pi.model.NewCorrelationSubscriptionMessageEventInfo;
import io.taktx.engine.pi.model.Scope;
import io.taktx.engine.pi.model.TerminateCorrelationSubscriptionMessageEventInfo;
import io.taktx.engine.pi.model.VariableScope;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class MessageSubscription extends Subscription {
  private String name;
  private String correlationKey;

  public MessageSubscription() {
    setOrder(5);
  }

  private MessageSubscription(
      ProcessInstanceProcessingContext processInstanceProcessingContext,
      VariableScope variableScope,
      IFlowNodeInstance parentFlowNodeInstance,
      SubScriptionType subScriptionType,
      FlowNode flowNodeToStart,
      Message message,
      FeelExpressionHandler feelExpressionHandler) {
    super(0, subScriptionType, flowNodeToStart != null ? flowNodeToStart.getId() : null);

    if (message == null) {
      throw new ProcessInstanceException(
          null, "Message event definition has no referenced message");
    }

    String correlationKeyExpression = message.correlationKey();
    JsonNode jsonNode =
        feelExpressionHandler.processFeelExpression(correlationKeyExpression, variableScope);
    if (jsonNode == null || jsonNode.isNull()) {
      throw new ProcessInstanceException(null, "Correlation key expression returned null");
    }
    String parsedCorrelationKey = jsonNode.asText();
    NewCorrelationSubscriptionMessageEventInfo messageInfo =
        new NewCorrelationSubscriptionMessageEventInfo(
            message.name(), parsedCorrelationKey, parentFlowNodeInstance, flowNodeToStart);
    processInstanceProcessingContext
        .getInstanceResult()
        .addNewCorrelationSubcriptionMessageEvent(messageInfo);

    this.name = message.name();
    this.correlationKey = parsedCorrelationKey;
  }

  public static MessageSubscription starting(
      ProcessInstanceProcessingContext processInstanceProcessingContext,
      VariableScope variableScope,
      IFlowNodeInstance parentFlowNodeInstance,
      FlowNode flowNodeToStart,
      Message message,
      FeelExpressionHandler feelExpressionHandler) {
    return new MessageSubscription(
        processInstanceProcessingContext,
        variableScope,
        parentFlowNodeInstance,
        SubScriptionType.STARTING,
        flowNodeToStart,
        message,
        feelExpressionHandler);
  }

  public static MessageSubscription continuing(
      ProcessInstanceProcessingContext processInstanceProcessingContext,
      VariableScope variableScope,
      IFlowNodeInstance flowNodeInstance,
      Message message,
      FeelExpressionHandler feelExpressionHandler) {
    return new MessageSubscription(
        processInstanceProcessingContext,
        variableScope,
        flowNodeInstance,
        SubScriptionType.CONTINUING,
        null,
        message,
        feelExpressionHandler);
  }

  @Override
  public boolean matchesEvent(EventSignal event) {
    return event instanceof MessageEventSignal messageEventSignal
        && name.equals(messageEventSignal.getName());
  }

  @Override
  public void cancel(
      ProcessInstanceProcessingContext processInstanceProcessingContext,
      Scope scope,
      FlowNodeInstance<?> instance) {
    TerminateCorrelationSubscriptionMessageEventInfo terminateEvent =
        new TerminateCorrelationSubscriptionMessageEventInfo(this.name, this.correlationKey);
    processInstanceProcessingContext
        .getInstanceResult()
        .addTerminateCorrelationSubscriptionMessageEvent(terminateEvent);
  }
}
